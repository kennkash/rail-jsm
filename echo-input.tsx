"use client"

/**
 * Echo Input Component
 * Chat input area with send functionality and space selector
 */

import { useState, useEffect } from 'react'
import { StopCircle, ChevronsUpDown, Check, Database, Globe } from 'lucide-react'
import {
  PromptInput,
  PromptInputTextarea,
  PromptInputToolbar,
  PromptInputTools,
  PromptInputButton,
  PromptInputSubmit,
} from '@/components/ui/shadcn-io/ai/prompt-input'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { useEchoAIStore } from '@/stores/echo-ai-store'
import { cn } from '@/lib/utils'
import { toast } from 'sonner'
import { useCurrentUser } from '@/hooks/use-current-user'
import type { EchoSpaceConfig, ConfluenceSpace } from '@/types/echo-ai.types'

const CONFLUENCE_ENDPOINT = 'https://confluence.samsungaustin.com/rest/scriptrunner/latest/custom/getIndexedSpaces'

interface EchoInputProps {
  className?: string
  placeholder?: string
  onSend?: (message: string, formattedQuery: string) => void
  value?: string
  onChangeValue?: (value: string) => void
  activeSpaceKey?: string | null
  prePrompt?: string
  // New props for space selection
  spaces?: EchoSpaceConfig[]
  onSpaceChange?: (spaceKey: string | null) => void
}

export function EchoInput({
  className,
  placeholder = "Ask Echo anything...",
  onSend,
  value,
  onChangeValue,
  activeSpaceKey,
  prePrompt,
  spaces = [],
  onSpaceChange,
}: EchoInputProps) {
  const [internalInput, setInternalInput] = useState('')
  const [spaceOpen, setSpaceOpen] = useState(false)
  const [availableSpaces, setAvailableSpaces] = useState<ConfluenceSpace[]>([])
  const [isLoadingSpaces, setIsLoadingSpaces] = useState(false)

  const { data: currentUser } = useCurrentUser()

  const isControlled = value !== undefined
  const input = isControlled ? value! : internalInput
  const setInput = (newValue: string) => {
    if (!isControlled) {
      setInternalInput(newValue)
    }
    onChangeValue?.(newValue)
  }
  const addMessage = useEchoAIStore((state) => state.addMessage)
  const isProcessing = useEchoAIStore((state) => state.isProcessing)
  const setIsProcessing = useEchoAIStore((state) => state.setIsProcessing)

  // Fetch available Confluence spaces for unrestricted mode
  useEffect(() => {
    const fetchSpaces = async () => {
      if (!currentUser?.key) return

      setIsLoadingSpaces(true)
      try {
        const url = new URL(CONFLUENCE_ENDPOINT)
        url.searchParams.append('username', currentUser.key)

        const response = await fetch(url.toString(), { credentials: 'same-origin' })
        if (!response.ok) throw new Error('Failed to fetch spaces')

        const data = (await response.json()) as ConfluenceSpace[]
        const filtered = data.filter((space) => space.hasViewPermission)
        setAvailableSpaces(filtered)
      } catch (error) {
        console.error('Error fetching Confluence spaces:', error)
      } finally {
        setIsLoadingSpaces(false)
      }
    }

    // Only fetch if no configured spaces (unrestricted mode)
    const safeLen = Array.isArray(spaces) ? spaces.length : 0
    if (safeLen === 0) {
      fetchSpaces()
    }
  }, [currentUser, spaces])

  // Determine which spaces to show in the combobox
  // Ensure spaces is always an array to prevent "t.forEach is not a function" errors
  const safeSpaces = Array.isArray(spaces) ? spaces : []
  const safeAvailableSpaces = Array.isArray(availableSpaces) ? availableSpaces : []

  const displaySpaces = safeSpaces.length > 0
    ? safeSpaces.map(s => ({ spaceKey: s.spaceKey, spaceName: s.spaceName, hasViewPermission: true }))
    : safeAvailableSpaces

  // Get current space name for display
  const currentSpace = displaySpaces.find(s => s.spaceKey === activeSpaceKey)
  const isSearchingAllSpaces = !activeSpaceKey

  const submitMessage = () => {
    const trimmedInput = input.trim()
    if (!trimmedInput || isProcessing) return

    // Format query with hidden space key prefix and optional pre-prompt
    // If no space is selected, don't add the #SPACE_KEY prefix (search all spaces)
    let formattedQuery = trimmedInput
    if (activeSpaceKey) {
      formattedQuery = `#${activeSpaceKey} `
      if (prePrompt && prePrompt.trim()) {
        formattedQuery += `${prePrompt.trim()} `
      }
      formattedQuery += trimmedInput
    } else {
      // No space selected - search all spaces (just use the raw query)
      if (prePrompt && prePrompt.trim()) {
        formattedQuery = `${prePrompt.trim()} ${trimmedInput}`
      }
    }

    addMessage({
      role: 'user',
      content: trimmedInput,
    })

    setInput('')

    if (onSend) {
      onSend(trimmedInput, formattedQuery)
    } else {
      setIsProcessing(true)
      setTimeout(() => {
        setIsProcessing(false)
        addMessage({
          role: 'assistant',
          content: `I'm processing your question: "${trimmedInput}"\n\nThis is a demo response. The AI integration is being configured to provide intelligent answers from your Confluence knowledge base.`,
        })
      }, 1500)
    }
  }

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    submitMessage()
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value)
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submitMessage()
    }
  }

  const handleSpaceSelect = (spaceKey: string | null) => {
    onSpaceChange?.(spaceKey)
    setSpaceOpen(false)
  }

  const handleStop = () => {
    setIsProcessing(false)
    toast.success('Processing stopped')
  }

  return (
    <PromptInput className={cn(className)} onSubmit={handleSubmit}>
      <PromptInputTextarea
        value={input}
        onChange={handleInputChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={isProcessing}
      />
      <PromptInputToolbar>
        <PromptInputTools>
          {/* Confluence Space Selector */}
          <Popover open={spaceOpen} onOpenChange={setSpaceOpen}>
            <PopoverTrigger asChild>
              <button
                type="button"
                disabled={isProcessing}
                className={cn(
                  "flex items-center gap-1.5 px-2 py-1 text-xs rounded-md transition-colors cursor-pointer",
                  "hover:bg-muted border border-transparent hover:border-border",
                  "text-muted-foreground hover:text-foreground",
                  "disabled:opacity-50 disabled:cursor-not-allowed"
                )}
              >
                {isSearchingAllSpaces ? (
                  <>
                    <Globe className="h-3.5 w-3.5" />
                    <span className="hidden sm:inline">All spaces</span>
                  </>
                ) : (
                  <>
                    <Database className="h-3.5 w-3.5" />
                    <span className="hidden sm:inline max-w-[120px] truncate">{currentSpace?.spaceName || activeSpaceKey}</span>
                  </>
                )}
                <ChevronsUpDown className="h-3 w-3 opacity-50" />
              </button>
            </PopoverTrigger>
            <PopoverContent className="w-[240px] p-0" align="start" side="top">
              <Command>
                <CommandInput placeholder="Search spaces..." className="h-9" />
                <CommandList>
                  <CommandEmpty>
                    {isLoadingSpaces ? 'Loading spaces...' : 'No spaces found'}
                  </CommandEmpty>
                  <CommandGroup>
                    {/* "All spaces" option - only show in unrestricted mode (no predefined spaces) */}
                    {safeSpaces.length === 0 && (
                      <CommandItem
                        value="__all__"
                        onSelect={() => handleSpaceSelect(null)}
                        className={cn(
                          "flex items-center gap-2 cursor-pointer",
                          isSearchingAllSpaces && "bg-accent text-accent-foreground"
                        )}
                      >
                        <Globe className="h-4 w-4 text-muted-foreground" />
                        <div className="flex flex-col">
                          <span className="text-sm font-medium">All spaces</span>
                          <span className="text-xs text-muted-foreground">Search across all available spaces</span>
                        </div>
                        {isSearchingAllSpaces && <Check className="ml-auto h-4 w-4" />}
                      </CommandItem>
                    )}
                    {/* Individual spaces */}
                    {displaySpaces.map((space) => (
                      <CommandItem
                        key={space.spaceKey}
                        value={space.spaceKey}
                        onSelect={() => handleSpaceSelect(space.spaceKey)}
                        className={cn(
                          "flex items-center gap-2 cursor-pointer",
                          activeSpaceKey === space.spaceKey && "bg-accent text-accent-foreground"
                        )}
                      >
                        <Database className="h-4 w-4 text-muted-foreground" />
                        <div className="flex flex-col min-w-0">
                          <span className="text-sm font-medium truncate">{space.spaceName}</span>
                          <span className="text-xs text-muted-foreground">{space.spaceKey}</span>
                        </div>
                        {activeSpaceKey === space.spaceKey && <Check className="ml-auto h-4 w-4" />}
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>
        </PromptInputTools>
        {isProcessing ? (
          <PromptInputButton variant="destructive" onClick={handleStop}>
            <StopCircle className="h-4 w-4" />
            Stop
          </PromptInputButton>
        ) : (
          <PromptInputSubmit disabled={!input.trim() || isProcessing} />
        )}
      </PromptInputToolbar>
    </PromptInput>
  )
}
