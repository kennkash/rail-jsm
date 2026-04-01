// /rail-at-sas/frontend/stores/admin-config-store.ts


import { create } from "zustand";
import type {
  GlobalConfig,
  AdminProjectInfo,
  AdminConfigState,
  EchoSettings,
  GeneralSettings,
} from "@/types/admin-config.types";
import { DEFAULT_GLOBAL_CONFIG } from "@/types/admin-config.types";
import {
  fetchGlobalConfig,
  saveGlobalConfig,
  fetchAdminProjects,
} from "@/lib/api/admin-client";

/**
 * Zustand store for Global Admin Configuration
 */
export const useAdminConfigStore = create<AdminConfigState>((set, get) => ({
  // Initial state
  config: { ...DEFAULT_GLOBAL_CONFIG },
  isLoading: false,
  isSaving: false,
  error: null,
  lastSaved: null,
  projects: [],
  projectsLoading: false,

  /**
   * Load global configuration from the server
   */
  loadConfig: async () => {
    set({ isLoading: true, error: null });

    try {
      const config = await fetchGlobalConfig();
      set({
        config: {
          ...DEFAULT_GLOBAL_CONFIG,
          ...config,
        },
        isLoading: false,
        error: null,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to load configuration";
      set({ isLoading: false, error: message });
      console.error("Failed to load admin config:", error);
    }
  },

  /**
   * Save global configuration to the server
   */
  saveConfig: async (updates: Partial<GlobalConfig>) => {
    const currentConfig = get().config;
    const newConfig = { ...currentConfig, ...updates };

    set({ isSaving: true, error: null });

    try {
      const savedConfig = await saveGlobalConfig(newConfig);
      set({
        config: {
          ...DEFAULT_GLOBAL_CONFIG,
          ...savedConfig,
        },
        isSaving: false,
        lastSaved: Date.now(),
        error: null,
      });
      return true;
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to save configuration";
      set({ isSaving: false, error: message });
      console.error("Failed to save admin config:", error);
      return false;
    }
  },

  /**
   * Load projects for the project picker
   */
  loadProjects: async () => {
    set({ projectsLoading: true });

    try {
      const response = await fetchAdminProjects();
      set({
        projects: response.projects.map((p) => ({
          id: p.id,
          key: p.key,
          name: p.name,
          description: p.description,
          avatarUrl: p.avatarUrl,
        })),
        projectsLoading: false,
      });
    } catch (error) {
      console.error("Failed to load projects:", error);
      set({ projectsLoading: false });
    }
  },

  /**
   * Add a project to the restricted list
   */
  addRestrictedProject: (projectKey: string) => {
    const { config } = get();
    const normalizedKey = projectKey.toUpperCase().trim();

    if (!normalizedKey) return;
    if (config.restrictedProjectKeys.includes(normalizedKey)) return;

    set({
      config: {
        ...config,
        restrictedProjectKeys: [...config.restrictedProjectKeys, normalizedKey],
      },
    });
  },

  /**
   * Remove a project from the restricted list
   */
  removeRestrictedProject: (projectKey: string) => {
    const { config } = get();
    const normalizedKey = projectKey.toUpperCase().trim();

    set({
      config: {
        ...config,
        restrictedProjectKeys: config.restrictedProjectKeys.filter(
          (key) => key !== normalizedKey
        ),
      },
    });
  },

  /**
   * Update Echo AI settings
   */
  updateEchoSettings: (settings: Partial<EchoSettings>) => {
    const { config } = get();

    set({
      config: {
        ...config,
        ...(settings.enabled !== undefined && { echoEnabled: settings.enabled }),
        ...(settings.masterPrompt !== undefined && { echoMasterPrompt: settings.masterPrompt }),
        ...(settings.maxTokens !== undefined && { echoMaxTokens: settings.maxTokens }),
        ...(settings.defaultModel !== undefined && { echoDefaultModel: settings.defaultModel }),
      },
    });
  },

  /**
   * Update General settings
   */
  updateGeneralSettings: (settings: Partial<GeneralSettings>) => {
    const { config } = get();

    set({
      config: {
        ...config,
        ...settings,
      },
    });
  },

  /**
   * Reset store to initial state
   */
  reset: () => {
    set({
      config: { ...DEFAULT_GLOBAL_CONFIG },
      isLoading: false,
      isSaving: false,
      error: null,
      lastSaved: null,
      projects: [],
      projectsLoading: false,
    });
  },
}));

