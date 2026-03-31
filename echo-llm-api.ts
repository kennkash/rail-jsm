/**
 * Normalize user-entered Echo query text so it is safe and stable
 * for the downstream Echo service.
 *
 * - Converts CR/LF/tab to spaces
 * - Removes control characters
 * - Collapses repeated whitespace
 * - Trims leading/trailing whitespace
 *
 * Intentionally preserves normal punctuation and quotes unless
 * you later confirm Echo also fails on those.
 */
export function normalizeEchoText(text: string): string {
  return text
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/[\x00-\x1F\x7F]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * Format a query with space key prefix
 * This allows the Echo service to scope searches to specific Confluence spaces
 */
export function formatQueryWithSpace(
  query: string,
  spaceKey?: string | null,
  prePrompt?: string
): string {
  const normalizedQuery = normalizeEchoText(query)
  const normalizedPrePrompt = prePrompt ? normalizeEchoText(prePrompt) : ''

  let formattedQuery = ''

  if (spaceKey) {
    formattedQuery = `#${spaceKey} `
  }

  if (normalizedPrePrompt) {
    formattedQuery += `${normalizedPrePrompt} `
  }

  formattedQuery += normalizedQuery

  return formattedQuery.trim()
}





// /rail-at-sas/frontend/lib/echo-llm-api.ts

/**
 * Echo AI - Samsung Echo Service Integration
 * 
 * This module handles communication with the Samsung Echo AI service
 * directly from the frontend using the two-step OAuth + Echo API flow.
 * 
 * Architecture:
 * Frontend -> OAuth (client_credentials) -> Samsung Echo Service (/confluence)
 * → SSE Streaming Response → parseStreamingResponse()
 */

// ============================================================================
// TYPES
// ============================================================================

/** Source reference from AI response */
export interface EchoLLMSource {
  title: string
  url: string
  description?: string // Summary/description of the page content
}

/** Parsed response from the LLM */
export interface EchoLLMResponse {
  sources: EchoLLMSource[]
  answer: string
  isComplete: boolean
}

/** Request payload for the Echo AI endpoint */
export interface EchoAIRequest {
  query: string
  endpoint?: 'confluence' | 'summary' | 'feedback'
  stream?: boolean
  // For feedback endpoint
  thumbs_up?: boolean
  category?: string
  comment?: string
  echo_content?: string
}

/** Health check response from the endpoint */
export interface EchoAIHealthResponse {
  status: string
  endpoint: string
  version: string
  authenticated: boolean
  user: string | null
  echoService: {
    url: string
    defaultEndpoint: string
    supportedEndpoints: string[]
  }
  capabilities: {
    streaming: boolean
    methods: string[]
    queryViaGet: boolean
  }
  timestamp: string
}

/** Error response from the endpoint */
export interface EchoAIError {
  error: boolean
  message: string
  statusCode: number
  timestamp: string
}

// ============================================================================
// CONFIGURATION
// ============================================================================

// OAuth (client credentials) configuration
const ECHO_OAUTH_TOKEN_URL =
  'https://auth.smartcloud.samsungaustin.com/realms/user_realm/protocol/openid-connect/token'
const ECHO_CLIENT_ID = 'i.castillo2'
const ECHO_CLIENT_SECRET = '86tyVoBTMzNePmmkjuVphzFLeu2K7aLIhDcL82Wcg'

// Echo API configuration
const ECHO_API_BASE_URL = 'http://echo.smartcloud.samsungaustin.com'
const ECHO_DEFAULT_ENDPOINT = 'confluence'

// Jira-hosted proxy endpoint to bypass browser CORS while still calling Echo directly (no ScriptRunner)
const ECHO_BROWSER_PROXY_ENDPOINT = '/rest/rail/1.0/echo'

// Token cache to avoid fetching on every request (uses expires_in when available)
const TOKEN_EXPIRY_BUFFER_MS = 60_000
let cachedToken: { accessToken: string | null; expiresAt: number | null } = {
  accessToken: null,
  expiresAt: null,
}

interface OAuthTokenResponse {
  access_token?: string
  expires_in?: number
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Parse sources from the response text
 * Looks for markdown links in the format: [Link Text](URL)
 * Also extracts the description/summary text that follows each link
 */
export function parseSources(text: string): EchoLLMSource[] {
  const sources: EchoLLMSource[] = []

  // Match markdown link followed by optional whitespace and description text
  // Pattern: [Title](URL) followed by text until next link or double newline
  const linkWithDescPattern = /\[([^\]]+)\]\(([^)]+)\)\s*\n?\s*([^\[]*?)(?=\n\s*\[|\n\n|$)/g

  let match
  while ((match = linkWithDescPattern.exec(text)) !== null) {
    const description = match[3]?.trim() || ''
    sources.push({
      title: match[1].trim(),
      url: match[2].trim(),
      description: description.length > 0 ? description : undefined,
    })
  }

  return sources.slice(0, 3) // Limit to 3 sources
}

/**
 * Parse the streaming response into sources and answer sections
 * Handles the actual Echo service format with specific markers:
 * - "**Confirm the AI Generated Answer with these source pages:**" (optional sources section)
 * - "**AI Generated Answer:**" (answer section marker)
 */
export function parseStreamingResponse(fullText: string): EchoLLMResponse {
  // Match actual Echo service format with specific markers
  const answerMarker = '**AI Generated Answer:**'
  const sourceMarker = '**Confirm the AI Generated Answer with these source pages:**'

  let sourcesText = ''
  let answerText = ''

  // Check if response contains the answer marker
  const answerIndex = fullText.indexOf(answerMarker)

  if (answerIndex !== -1) {
    // Extract everything before the answer marker (sources section)
    const beforeAnswer = fullText.substring(0, answerIndex)

    // Check if there's a source marker
    const sourceIndex = beforeAnswer.indexOf(sourceMarker)
    if (sourceIndex !== -1) {
      // Extract source section (between source marker and answer marker)
      sourcesText = beforeAnswer.substring(sourceIndex + sourceMarker.length).trim()
    }

    // Extract answer (everything after the answer marker)
    // IMPORTANT: Keep empty string if no answer content yet - don't fall back to fullText
    // This prevents showing raw sources/markers during streaming
    answerText = fullText.substring(answerIndex + answerMarker.length).trim()
  } else {
    // No answer marker found yet - return empty answer
    // This keeps the loading state visible until the actual answer starts streaming
    answerText = ''
  }

  // Parse sources from the dedicated sources section only
  // Don't parse from answer text to avoid extracting links that are part of the response
  const sources = parseSources(sourcesText)

  return {
    sources,
    answer: answerText, // Return empty string if no answer yet - caller handles this
    isComplete: answerText.length > 0,
  }
}

/**
 * Format a query with space key prefix
 * This allows the Echo service to scope searches to specific Confluence spaces
 */
export function formatQueryWithSpace(
  query: string,
  spaceKey?: string | null,
  prePrompt?: string
): string {
  let formattedQuery = ''

  if (spaceKey) {
    formattedQuery = `#${spaceKey} `
  }

  if (prePrompt && prePrompt.trim()) {
    formattedQuery += `${prePrompt.trim()} `
  }

  formattedQuery += query

  return formattedQuery
}

// ============================================================================
// AUTH / REQUEST HELPERS
// ============================================================================

function buildEchoUrl(endpoint: string) {
  const cleanedEndpoint = endpoint.replace(/^\/+/, '').replace(/\/+$/, '')
  return `${ECHO_API_BASE_URL}/${cleanedEndpoint}/`
}

async function getEchoAccessToken(): Promise<string> {
  const now = Date.now()

  if (cachedToken.accessToken && cachedToken.expiresAt && cachedToken.expiresAt > now) {
    return cachedToken.accessToken
  }

  const body = new URLSearchParams({
    grant_type: 'client_credentials',
    client_id: ECHO_CLIENT_ID,
    client_secret: ECHO_CLIENT_SECRET,
  })

  const response = await fetch(ECHO_OAUTH_TOKEN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: body.toString(),
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => '')
    throw new Error(
      `Failed to fetch Echo OAuth token (${response.status}): ${errorText || 'no response body'}`
    )
  }

  const data = (await response.json()) as OAuthTokenResponse
  if (!data.access_token) {
    throw new Error('Echo OAuth token response did not include access_token')
  }

  const expiresInMs =
    typeof data.expires_in === 'number' && data.expires_in > 0
      ? data.expires_in * 1000
      : null

  cachedToken = {
    accessToken: data.access_token,
    expiresAt: expiresInMs ? now + Math.max(0, expiresInMs - TOKEN_EXPIRY_BUFFER_MS) : null,
  }

  return data.access_token
}

// ============================================================================
// MAIN API FUNCTIONS
// ============================================================================

/**
 * Check the health/status of the Echo AI endpoint
 */
export async function checkEchoAIHealth(): Promise<EchoAIHealthResponse> {
  try {
    const response = await fetch(ECHO_BROWSER_PROXY_ENDPOINT, {
      method: 'GET',
    })
    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new Error(text || `Health check failed: ${response.status}`)
    }

    return (await response.json()) as EchoAIHealthResponse
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    throw new Error(`Health check failed: ${message}`)
  }
}

/**
 * Query the Echo AI service via POST
 * 
 * @param query - The user's question
 * @param options - Optional configuration
 * @param onChunk - Callback for each streaming chunk
 * @param onComplete - Callback when streaming is complete
 * @param onError - Callback for errors
 */
export async function queryEchoAI(
  query: string,
  options: {
    endpoint?: 'confluence' | 'summary' | 'feedback'
    stream?: boolean
    spaceKey?: string | null
    prePrompt?: string
  } = {},
  onChunk?: (chunk: string, fullText: string) => void,
  onComplete?: (response: EchoLLMResponse) => void,
  onError?: (error: Error) => void
): Promise<void> {
  try {
    const endpoint = options.endpoint || ECHO_DEFAULT_ENDPOINT

    if (endpoint === 'feedback') {
      throw new Error('Feedback requests should use submitEchoFeedback()')
    }

    // Format the query with space key and pre-prompt
    const formattedQuery = formatQueryWithSpace(
      query,
      options.spaceKey,
      options.prePrompt
    )
    
    const streamResponse = options.stream ?? true
    const useProxy = typeof window !== 'undefined'
    const targetUrl = useProxy ? ECHO_BROWSER_PROXY_ENDPOINT : buildEchoUrl(endpoint)

    const requestBody =
      endpoint === 'summary'
        ? {
            endpoint,
            page_id: formattedQuery,
            stream: streamResponse,
          }
        : {
            endpoint,
            query: formattedQuery,
            stream: streamResponse,
          }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }

    if (!useProxy) {
      const accessToken = await getEchoAccessToken()
      headers.Authorization = `Bearer ${accessToken}`
    }

    const response = await fetch(targetUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify(requestBody),
    })

    if (!response.ok) {
      const errorText = await response.text().catch(() => '')
      throw new Error(
        `Echo service error (${response.status}): ${errorText || 'no response body'}`
      )
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('Response body is not readable')
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let fullText = ''
    let chunkCount = 0
    let totalBytes = 0
    let emittedEvents = 0
    let eventLines: string[] = []

    const flushEventBuffer = () => {
      if (eventLines.length === 0) {
        return
      }
      const payload = eventLines.join('\n').trim()
      eventLines = []
      if (!payload || payload === '[DONE]') {
        return
      }
      // Add newline between SSE events to preserve formatting
      // Don't add newline for the first event
      if (fullText.length > 0 && !fullText.endsWith('\n')) {
        fullText += '\n'
      }
      fullText += payload
      emittedEvents++
      onChunk?.(payload, fullText)
    }

    const handleLine = (line: string) => {
      const trimmedLine = line.replace(/\r$/, '')
      if (trimmedLine.length === 0) {
        flushEventBuffer()
        return
      }
      if (trimmedLine.startsWith('data:')) {
        eventLines.push(trimmedLine.slice(5).trimStart())
        return
      }
      if (trimmedLine.startsWith(':')) {
        // SSE heartbeat/comment
        return
      }
      eventLines.push(trimmedLine)
    }

    const processBuffer = (flushRemainder = false) => {
      let newlineIndex
      while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
        const line = buffer.slice(0, newlineIndex)
        buffer = buffer.slice(newlineIndex + 1)
        handleLine(line)
      }
      if (flushRemainder && buffer.length > 0) {
        handleLine(buffer)
        buffer = ''
      }
    }

    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        break
      }

      chunkCount++
      totalBytes += value.length
      const textChunk = decoder.decode(value, { stream: true })
      buffer += textChunk

      processBuffer()
    }

    processBuffer(true)
    flushEventBuffer()

    if (fullText.length === 0) {
      onError?.(new Error('Received empty response from Echo AI service'))
      return
    }

    const parsedResponse = parseStreamingResponse(fullText)
    onComplete?.(parsedResponse)

  } catch (error) {
    console.error('Echo AI error:', error)
    onError?.(error as Error)
  }
}

/**
 * Query using GET method (simpler, but less flexible)
 * Good for simple queries without needing POST body
 */
export async function queryEchoAIGet(
  query: string,
  options: {
    endpoint?: string
    spaceKey?: string | null
    prePrompt?: string
  } = {},
  onChunk?: (chunk: string, fullText: string) => void,
  onComplete?: (response: EchoLLMResponse) => void,
  onError?: (error: Error) => void
): Promise<void> {
  // The direct Echo API does not support GET for queries; delegate to POST flow
  return queryEchoAI(
    query,
    {
      endpoint: (options.endpoint as 'confluence' | 'summary' | 'feedback') || ECHO_DEFAULT_ENDPOINT,
      stream: true,
      spaceKey: options.spaceKey,
      prePrompt: options.prePrompt,
    },
    onChunk,
    onComplete,
    onError
  )
}

/**
 * Submit feedback for an Echo AI response
 */
export async function submitEchoFeedback(feedback: {
  thumbsUp: boolean
  category?: string
  comment?: string
  echoContent?: string
}): Promise<boolean> {
  try {
    const response = await fetch(ECHO_BROWSER_PROXY_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        endpoint: 'feedback',
        thumbs_up: feedback.thumbsUp,
        category: feedback.category,
        comment: feedback.comment,
        source: 'confluence',
        echo_content: feedback.echoContent,
      }),
    })

    if (!response.ok) {
      const errorText = await response.text().catch(() => '')
      throw new Error(
        `Echo feedback request failed (${response.status}): ${errorText || 'no response body'}`
      )
    }

    return true
  } catch (error) {
    console.error('[Echo AI] Feedback error:', error)
    return false
  }
}

// ============================================================================
// LEGACY FUNCTIONS (Backward Compatibility)
// ============================================================================

/**
 * Legacy function - Query the Confluence LLM endpoint
 * @deprecated Use queryEchoAI instead
 */
export async function queryConfluenceLLM(
  formattedQuery: string,
  onChunk?: (chunk: string, fullText: string) => void,
  onComplete?: (response: EchoLLMResponse) => void,
  onError?: (error: Error) => void
): Promise<void> {
  // Use the new POST-based API
  await queryEchoAI(
    formattedQuery,
    { stream: true },
    onChunk,
    onComplete,
    onError
  )
}

// ============================================================================
// TYPE ALIASES FOR BACKWARD COMPATIBILITY
// ============================================================================

// Re-export with legacy names
export type ChatMessage = {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export function buildConversationMessages(
  messages: Array<{ role: 'user' | 'assistant' | 'system'; content: string }>,
  systemPrompt?: string,
  spaceKey?: string | null,
  prePrompt?: string
): ChatMessage[] {
  // This function is kept for compatibility but the new Echo API
  // handles context differently (through space keys and pre-prompts)
  const result: ChatMessage[] = []
  
  if (systemPrompt) {
    result.push({ role: 'system', content: systemPrompt })
  }
  
  for (const msg of messages) {
    result.push({ role: msg.role, content: msg.content })
  }
  
  return result
}

// Alias for backward compatibility
export const queryLocalLLM = queryEchoAI
export const checkEchoLLMHealth = checkEchoAIHealth
export type EchoLLMHealthResponse = EchoAIHealthResponse
