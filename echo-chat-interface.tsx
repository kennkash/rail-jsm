// /rail-at-sas/frontend/components/echo-ai/echo-chat-interface.tsx

"use client"

/**
 * Echo Chat Interface Component
 * Main chat UI that combines all Echo components
 */

import { useEffect, useState } from 'react'
import { Settings, PanelRightClose, AlertCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { EchoMessageList } from './echo-message-list'
import { EchoInput } from './echo-input'
import { EchoSettingsPanel } from './echo-settings-panel'
import { useEchoAIStore } from '@/stores/echo-ai-store'
import { cn } from '@/lib/utils'
import type { EchoMode, EchoSpaceConfig } from '@/types/echo-ai.types'
import {
  queryEchoAI,
  parseStreamingResponse,
  type EchoLLMResponse,
} from '@/lib/echo-llm-api'
import { toast } from 'sonner'

interface EchoChatInterfaceProps {
  mode?: EchoMode
  className?: string
  showSettings?: boolean
  onClose?: () => void
  // Dynamic configuration from saved component settings
  header?: string
  subheader?: string
  inputPlaceholder?: string
  spaces?: EchoSpaceConfig[]
  activeSpaceKey?: string | null
}

export function EchoChatInterface({
  mode = 'customer',
  className,
  showSettings = true,
  onClose,
  header: propHeader,
  subheader: propSubheader,
  inputPlaceholder: propInputPlaceholder,
  spaces: propSpaces,
  activeSpaceKey: propActiveSpaceKey,
}: EchoChatInterfaceProps) {
  const setMode = useEchoAIStore((state) => state.setMode)
  const messages = useEchoAIStore((state) => state.messages)
  const addMessage = useEchoAIStore((state) => state.addMessage)
  const setIsProcessing = useEchoAIStore((state) => state.setIsProcessing)
  const upsertStreamingAssistantMessage = useEchoAIStore(
    (state) => state.upsertStreamingAssistantMessage
  )

  // Use props if provided (customer mode), otherwise use store (builder mode)
  const storeHeader = useEchoAIStore((state) => state.header)
  const storeSubheader = useEchoAIStore((state) => state.subheader)
  const storeInputPlaceholder = useEchoAIStore((state) => state.inputPlaceholder)
  const storeSpaces = useEchoAIStore((state) => state.spaces)
  const storeActiveSpaceKey = useEchoAIStore((state) => state.activeSpaceKey)
  const setStoreActiveSpaceKey = useEchoAIStore((state) => state.setActiveSpaceKey)

  const header = propHeader ?? storeHeader
  const subheader = propSubheader ?? storeSubheader
  const inputPlaceholder = propInputPlaceholder ?? storeInputPlaceholder

  // Ensure spaces is always an array to prevent "t.forEach is not a function" errors
  const rawSpaces = propSpaces ?? storeSpaces
  const spaces = Array.isArray(rawSpaces) ? rawSpaces : []

  // Derive an effective active space key:
  // 1) Prefer explicit prop (from portal component/customer portal config)
  // 2) Fallback to store value (builder/global state)
  // 3) Fallback to first configured space, if any
  const baseActiveSpaceKey = propActiveSpaceKey ?? storeActiveSpaceKey
  const fallbackActiveSpaceKey = spaces.length > 0 ? spaces[0].spaceKey : null
  const activeSpaceKey = baseActiveSpaceKey ?? fallbackActiveSpaceKey

  // Keep the store's active space in sync when it hasn't been set yet
  useEffect(() => {
    if (!storeActiveSpaceKey && activeSpaceKey) {
      setStoreActiveSpaceKey(activeSpaceKey)
    }
  }, [activeSpaceKey, storeActiveSpaceKey, setStoreActiveSpaceKey])

  const [inputValue, setInputValue] = useState('')

  useEffect(() => {
    setMode(mode)
  }, [mode, setMode])

  // Load mock messages on mount for demo purposes
  // DISABLED: Show intro view in builder mode instead of demo messages
  // useEffect(() => {
  //   if (mode === 'builder') {
  //     loadMockMessages()
  //   }
  // }, [mode, loadMockMessages])

  // Get active space's pre-prompt
  const activeSpace = spaces.find((space) => space.spaceKey === activeSpaceKey)
  const prePrompt = activeSpace?.prePrompt

  /**
   * Sanitize text for safe inclusion in query string
   * Removes/replaces characters that could break JSON parsing
   */
  const sanitizeForQuery = (text: string): string => {
    return text
      // Replace newlines and tabs with spaces
      .replace(/[\n\r\t]/g, ' ')
      // Replace quotes with single quotes
      .replace(/"/g, "'")
      // Remove control characters
      .replace(/[\x00-\x1F\x7F]/g, '')
      // Collapse multiple spaces
      .replace(/\s+/g, ' ')
      .trim()
  }

  /**
   * Build conversation context from existing messages for follow-up questions
   * This helps Echo AI understand the context of the conversation
   */
  const buildConversationContext = (): string => {
    if (messages.length === 0) return ''

    // Get the last user message and assistant response
    const recentMessages = messages.slice(-2) // Get last 2 messages (user + assistant)
    const contextParts: string[] = []

    for (const msg of recentMessages) {
      if (msg.role === 'user') {
        const cleanContent = sanitizeForQuery(msg.content)
        contextParts.push(`Previously asked: ${cleanContent}`)
      } else if (msg.role === 'assistant' && msg.content && !msg.isLoading) {
        // Truncate long responses to keep context manageable
        const truncated = msg.content.length > 200
          ? msg.content.substring(0, 200)
          : msg.content
        const cleanContent = sanitizeForQuery(truncated)
        contextParts.push(`Previous answer: ${cleanContent}`)
      }
    }

    if (contextParts.length === 0) return ''

    return `[Context: ${contextParts.join(' -- ')}] `
  }

  /**
   * Send query to Echo AI service using the direct OAuth + Echo API flow
   * (token request → bearer call to echo.smartcloud.samsungaustin.com/confluence)
   */
  const sendToLLM = async (displayMessage: string, _formattedQuery: string, includeContext: boolean = false) => {
    // Build the effective pre-prompt, optionally including conversation context
    let effectivePrePrompt = prePrompt || ''
    if (includeContext) {
      const conversationContext = buildConversationContext()
      if (conversationContext) {
        effectivePrePrompt = conversationContext + effectivePrePrompt
      }
    }

    setIsProcessing(true)
    upsertStreamingAssistantMessage('', { isFinal: false })

    // Track streaming content
    let streamingContent = ''
    let chunkCount = 0

    try {
      await queryEchoAI(
        displayMessage,  // The user's question
        {
          endpoint: 'confluence',
          stream: true,
          spaceKey: activeSpaceKey,  // Will be formatted as #SPACE_KEY prefix
          prePrompt: effectivePrePrompt || undefined,  // Pre-prompt with optional conversation context
        },
        // onChunk: Stream answer content as it arrives (sources added only in onComplete)
        (chunk, fullText) => {
          chunkCount++
          streamingContent = fullText

          // Only display content after we've received the answer marker AND actual answer content
          const answerMarker = '**AI Generated Answer:**'
          const hasAnswerMarker = fullText.includes(answerMarker)

          if (hasAnswerMarker) {
            // Parse the streaming response to extract only the answer portion
            const parsed = parseStreamingResponse(fullText)
            const displayContent = parsed.answer || ''

            // CRITICAL: Only update message if we have actual answer content
            // Don't add sources during streaming - they're added in onComplete
            // This prevents the visual glitch where content appears then disappears
            if (displayContent && displayContent.trim().length > 0) {
              upsertStreamingAssistantMessage(displayContent, {
                isFinal: false,
              })
            }
          }
        },
        (response: EchoLLMResponse) => {
          setIsProcessing(false)
          const finalContent = response.answer?.trim() || 'Echo AI could not generate a response. Please try again.'
          upsertStreamingAssistantMessage(finalContent, {
            isFinal: true,
            sources: response.sources,
          })
        },
        (error: Error) => {
          setIsProcessing(false)
          toast.error('Failed to get response', {
            description: error.message,
          })

          upsertStreamingAssistantMessage(
            `I encountered an error while processing your question: "${displayMessage}". Please try again or contact support if the issue persists.\n\nError: ${error.message}`,
            { isFinal: true }
          )
        }
      )
    } catch (error) {
      console.error('Echo Chat exception:', error)
      setIsProcessing(false)
    }
  }

  const handlePromptCardClick = (prompt: string) => {
    // Check if there are existing messages - if so, this is a follow-up question
    // and we should include conversation context
    const isFollowUp = messages.length > 0

    // Format query with hidden space key prefix
    // If no space is selected, don't add prefix (search all spaces)
    let formattedQuery = prompt
    if (activeSpaceKey) {
      formattedQuery = `#${activeSpaceKey} `
      if (prePrompt && prePrompt.trim()) {
        formattedQuery += `${prePrompt.trim()} `
      }
      formattedQuery += prompt
    } else if (prePrompt && prePrompt.trim()) {
      formattedQuery = `${prePrompt.trim()} ${prompt}`
    }

    // Immediately submit the prompt as a user message
    addMessage({
      role: 'user',
      content: prompt,
    })

    // Clear input
    setInputValue('')

    // Send to LLM endpoint (customer mode) or simulate (builder mode)
    if (mode === 'customer') {
      // Always send to LLM - no space selected means search all spaces
      // Include conversation context if this is a follow-up question
      sendToLLM(prompt, formattedQuery, isFollowUp)
    } else {
      // Simulate processing in builder mode
      setIsProcessing(true)
      setTimeout(() => {
        setIsProcessing(false)

        // Add mock response
        addMessage({
          role: 'assistant',
          content: `I'm processing your question: "${prompt}"\n\nThis is a demo response. The AI integration is being configured to provide intelligent answers from your knowledge base.`,
        })
      }, 1500)
    }
  }

  return (
    <div
      className={cn(
        "flex h-full flex-col bg-background",
        mode === 'customer' && "border-l",
        className
      )}
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b bg-background px-4 py-3">
        <div className="flex-1">
          <h2 className="text-sm font-semibold">{header}</h2>
          <p className="text-xs text-muted-foreground">
            {subheader}
          </p>
        </div>

        <div className="flex items-center gap-1">
          {showSettings && mode === 'builder' && (
            <Sheet>
              <SheetTrigger asChild>
                <Button variant="ghost" size="sm">
                  <Settings className="h-4 w-4" />
                  <span className="sr-only">Settings</span>
                </Button>
              </SheetTrigger>
              <SheetContent side="right" className="w-full sm:max-w-md">
                <SheetHeader>
                  <SheetTitle>Echo AI Configuration</SheetTitle>
                </SheetHeader>
                <div className="mt-4">
                  <EchoSettingsPanel />
                </div>
              </SheetContent>
            </Sheet>
          )}

          {onClose && (
            <Button variant="ghost" size="sm" onClick={onClose}>
              <PanelRightClose className="h-4 w-4" />
              <span className="sr-only">Collapse</span>
            </Button>
          )}
        </div>
      </div>

      {/* AI Accuracy Disclaimer */}
      {mode === 'customer' && (
        <div className="border-b bg-blue-50 dark:bg-blue-950/20 px-4 py-2.5">
          <div className="flex items-start gap-2">
            <AlertCircle className="h-4 w-4 text-blue-600 dark:text-blue-400 mt-0.5 flex-shrink-0" />
            <div className="text-xs text-blue-800 dark:text-blue-200">
              <p className="font-medium mb-0.5">AI-powered assistant</p>
              <p className="opacity-90">Echo is a chatbot that can answer your questions based on the content of SAS Confluence spaces. Echo can make mistakes. Verify the information via the referenced pages.</p>
            </div>
          </div>
        </div>
      )}

      {/* Messages Area */}
      <div className="flex-1 overflow-hidden">
        <EchoMessageList
          onPromptCardClick={handlePromptCardClick}
          spaces={spaces}
          activeSpaceKey={activeSpaceKey}
        />
      </div>



      {/* Input Area */}
      <div className="border-t bg-background px-4 py-4">
        <EchoInput
          placeholder={inputPlaceholder}
          value={inputValue}
          onChangeValue={setInputValue}
          activeSpaceKey={activeSpaceKey}
          prePrompt={prePrompt}
          spaces={spaces}
          onSpaceChange={(spaceKey) => {
            setStoreActiveSpaceKey(spaceKey)
          }}
          onSend={
            mode === 'customer'
              ? (message, formattedQuery) => {
                  // In customer mode, always send to LLM
                  // If no space is selected, formattedQuery won't have #SPACE_KEY prefix
                  // which means the API will search across all available spaces
                  sendToLLM(message, formattedQuery)
                }
              : undefined
          }
        />
      </div>
    </div>
  )
}
