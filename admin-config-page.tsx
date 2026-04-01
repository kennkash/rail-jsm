// /rail-at-sas/frontend/components/admin/admin-config-page.tsx

"use client";

import { useEffect, useState, useCallback } from "react";
import { useAdminConfigStore } from "@/stores/admin-config-store";
import { Loader2, Save, AlertCircle, Check } from "lucide-react";
import { ADMIN_TABS, type AdminTab } from "@/types/admin-config.types";
import { GeneralSettingsTab } from "./general-settings-tab";
import { RestrictedProjectsTab } from "./restricted-projects-tab";
import { EchoAiSettingsTab } from "./echo-ai-settings-tab";

/**
 * Global Admin Configuration Page
 * Native Atlassian admin styling
 */
export function AdminConfigPage() {
  const [activeTab, setActiveTab] = useState<AdminTab>("general-settings");
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [justSaved, setJustSaved] = useState(false);
  const {
    config,
    isLoading,
    isSaving,
    error,
    loadConfig,
    saveConfig,
  } = useAdminConfigStore();

  // Load config on mount
  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  // Warn about unsaved changes when switching tabs
  const handleTabChange = useCallback((newTab: AdminTab) => {
    if (hasUnsavedChanges) {
      const confirmed = window.confirm("You have unsaved changes. Are you sure you want to switch tabs?");
      if (!confirmed) return;
    }
    setActiveTab(newTab);
    setHasUnsavedChanges(false);
  }, [hasUnsavedChanges]);

  const handleSave = async () => {
    const success = await saveConfig(config);
    if (success) {
      setHasUnsavedChanges(false);
      setJustSaved(true);
      setTimeout(() => setJustSaved(false), 2000);
    }
  };

  const markDirty = useCallback(() => {
    setHasUnsavedChanges(true);
    setJustSaved(false);
  }, []);

  if (isLoading) {
    return (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "200px" }}>
        <Loader2 className="h-6 w-6 animate-spin" style={{ color: "#6b778c" }} />
        <span style={{ marginLeft: "8px", color: "#6b778c" }}>Loading configuration...</span>
      </div>
    );
  }

  // Save button styles based on state
  const getSaveButtonStyle = (): React.CSSProperties => {
    if (isSaving) {
      return { backgroundColor: "#0052cc", color: "#fff", opacity: 0.7, cursor: "not-allowed" };
    }
    if (justSaved) {
      return { backgroundColor: "#36b37e", color: "#fff", cursor: "default" };
    }
    if (!hasUnsavedChanges) {
      return { backgroundColor: "#f4f5f7", color: "#a5adba", cursor: "not-allowed" };
    }
    return { backgroundColor: "#0052cc", color: "#fff", cursor: "pointer" };
  };

  return (
    <div style={{ padding: "20px 0", maxWidth: "800px" }}>
      {/* Header */}
      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: "24px",
        paddingBottom: "16px",
        borderBottom: "1px solid #dfe1e6"
      }}>
        <div>
          <h2 style={{ margin: 0, fontSize: "20px", fontWeight: 500, color: "#172b4d" }}>
            RAIL Portal Configuration
          </h2>
          <p style={{ margin: "4px 0 0", fontSize: "14px", color: "#6b778c" }}>
            Global settings for RAIL Portal
          </p>
        </div>
        <button
          onClick={handleSave}
          disabled={isSaving || (!hasUnsavedChanges && !justSaved)}
          style={{
            display: "flex",
            alignItems: "center",
            gap: "6px",
            padding: "8px 16px",
            fontSize: "14px",
            fontWeight: 500,
            border: "none",
            borderRadius: "3px",
            ...getSaveButtonStyle()
          }}
        >
          {isSaving ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
              Saving...
            </>
          ) : justSaved ? (
            <>
              <Check className="h-4 w-4" />
              Saved!
            </>
          ) : (
            <>
              <Save className="h-4 w-4" />
              Save Changes
            </>
          )}
        </button>
      </div>

      {/* Error display */}
      {error && (
        <div style={{
          padding: "12px 16px",
          marginBottom: "16px",
          backgroundColor: "#ffebe6",
          borderRadius: "3px",
          display: "flex",
          alignItems: "center",
          gap: "8px"
        }}>
          <AlertCircle style={{ height: "16px", width: "16px", color: "#de350b" }} />
          <span style={{ color: "#de350b", fontSize: "14px" }}>{error}</span>
        </div>
      )}

      {/* Tab navigation */}
      <div style={{
        display: "flex",
        gap: "0",
        borderBottom: "2px solid #dfe1e6",
        marginBottom: "24px"
      }}>
        {ADMIN_TABS.map((tab) => (
          <button
            key={tab.id}
            onClick={() => !tab.disabled && handleTabChange(tab.id)}
            disabled={tab.disabled}
            style={{
              padding: "12px 16px",
              fontSize: "14px",
              fontWeight: activeTab === tab.id ? 500 : 400,
              color: tab.disabled ? "#a5adba" : activeTab === tab.id ? "#0052cc" : "#42526e",
              backgroundColor: "transparent",
              border: "none",
              borderBottom: activeTab === tab.id ? "2px solid #0052cc" : "2px solid transparent",
              marginBottom: "-2px",
              cursor: tab.disabled ? "not-allowed" : "pointer",
              display: "flex",
              alignItems: "center",
              gap: "6px"
            }}
          >
            {tab.label}
            {tab.comingSoon && (
              <span style={{
                fontSize: "11px",
                padding: "2px 6px",
                backgroundColor: "#dfe1e6",
                borderRadius: "3px",
                color: "#6b778c"
              }}>
                Soon
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div>
        {activeTab === "general-settings" && <GeneralSettingsTab onDirty={markDirty} />}
        {activeTab === "restricted-projects" && <RestrictedProjectsTab onDirty={markDirty} />}
        {activeTab === "echo-ai" && <EchoAiSettingsTab onDirty={markDirty} />}
      </div>
    </div>
  );
}

