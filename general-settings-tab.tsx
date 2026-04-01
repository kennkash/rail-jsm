// /rail-at-sas/frontend/components/admin/general-settings-tab.tsx

"use client";

import { useAdminConfigStore } from "@/stores/admin-config-store";

interface Props {
  onDirty?: () => void;
}

/**
 * General Settings Tab
 * Native Atlassian admin styling - single column, checkboxes
 */
export function GeneralSettingsTab({ onDirty }: Props) {
  const { config, updateGeneralSettings } = useAdminConfigStore();

  const handleChange = (updates: Parameters<typeof updateGeneralSettings>[0]) => {
    updateGeneralSettings(updates);
    onDirty?.();
  };

  // Native Atlassian field styles
  const labelStyle: React.CSSProperties = {
    display: "block",
    marginBottom: "4px",
    fontSize: "12px",
    fontWeight: 600,
    color: "#6b778c"
  };
  const inputStyle: React.CSSProperties = {
    width: "100%",
    maxWidth: "400px",
    padding: "8px 10px",
    fontSize: "14px",
    border: "2px solid #dfe1e6",
    borderRadius: "3px",
    backgroundColor: "#fafbfc"
  };
  const selectStyle: React.CSSProperties = {
    ...inputStyle,
    cursor: "pointer"
  };
  const helpTextStyle: React.CSSProperties = {
    fontSize: "11px",
    color: "#6b778c",
    marginTop: "4px"
  };
  const sectionStyle: React.CSSProperties = {
    marginBottom: "32px",
    paddingBottom: "24px",
    borderBottom: "1px solid #ebecf0"
  };
  const sectionTitleStyle: React.CSSProperties = {
    fontSize: "14px",
    fontWeight: 600,
    color: "#172b4d",
    marginBottom: "4px"
  };
  const sectionDescStyle: React.CSSProperties = {
    fontSize: "12px",
    color: "#6b778c",
    marginBottom: "16px"
  };
  const fieldGroupStyle: React.CSSProperties = {
    marginBottom: "16px"
  };
  const checkboxRowStyle: React.CSSProperties = {
    display: "flex",
    alignItems: "flex-start",
    gap: "8px",
    marginBottom: "12px"
  };

  return (
    <div>
      {/* Portal Branding */}
      <div style={sectionStyle}>
        <h3 style={sectionTitleStyle}>Portal Branding</h3>
        <p style={sectionDescStyle}>Customize the appearance and branding of RAIL portals.</p>

        <div style={fieldGroupStyle}>
          <label style={labelStyle}>Portal Title</label>
          <input
            type="text"
            placeholder="RAIL Portal"
            value={config.portalTitle}
            onChange={(e) => handleChange({ portalTitle: e.target.value })}
            style={inputStyle}
          />
          <p style={helpTextStyle}>The main title displayed on portal pages.</p>
        </div>

        <div style={fieldGroupStyle}>
          <label style={labelStyle}>Portal Subtitle</label>
          <input
            type="text"
            placeholder="Request Assistance & Information Library"
            value={config.portalSubtitle}
            onChange={(e) => handleChange({ portalSubtitle: e.target.value })}
            style={inputStyle}
          />
          <p style={helpTextStyle}>A subtitle or tagline shown below the main title.</p>
        </div>

        <div style={fieldGroupStyle}>
          <label style={labelStyle}>Logo URL</label>
          <input
            type="text"
            placeholder="https://example.com/logo.png"
            value={config.portalLogoUrl}
            onChange={(e) => handleChange({ portalLogoUrl: e.target.value })}
            style={inputStyle}
          />
          <p style={helpTextStyle}>URL to a custom logo image. Leave empty to use the default.</p>
        </div>

        <div style={checkboxRowStyle}>
          <input
            type="checkbox"
            id="showPoweredByRail"
            checked={config.showPoweredByRail}
            onChange={(e) => handleChange({ showPoweredByRail: e.target.checked })}
            style={{ marginTop: "2px" }}
          />
          <label htmlFor="showPoweredByRail" style={{ fontSize: "14px", color: "#172b4d", cursor: "pointer" }}>
            Show "Powered by RAIL" in portal footer
          </label>
        </div>
      </div>

      {/* Support Contact */}
      <div style={sectionStyle}>
        <h3 style={sectionTitleStyle}>Support Contact</h3>
        <p style={sectionDescStyle}>Configure support contact information displayed in portals.</p>

        <div style={fieldGroupStyle}>
          <label style={labelStyle}>Support Email</label>
          <input
            type="email"
            placeholder="support@example.com"
            value={config.supportEmail}
            onChange={(e) => handleChange({ supportEmail: e.target.value })}
            style={inputStyle}
          />
        </div>

        <div style={fieldGroupStyle}>
          <label style={labelStyle}>Support URL</label>
          <input
            type="url"
            placeholder="https://support.example.com"
            value={config.supportUrl}
            onChange={(e) => handleChange({ supportUrl: e.target.value })}
            style={inputStyle}
          />
        </div>
      </div>

      {/* Appearance */}
      <div style={sectionStyle}>
        <h3 style={sectionTitleStyle}>Appearance</h3>
        <p style={sectionDescStyle}>Configure default appearance settings.</p>

        <div style={fieldGroupStyle}>
          <label style={labelStyle}>Default Theme</label>
          <select
            value={config.defaultTheme}
            onChange={(e) => handleChange({ defaultTheme: e.target.value as "light" | "dark" | "system" })}
            style={{ ...selectStyle, width: "200px" }}
          >
            <option value="system">System</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
          <p style={helpTextStyle}>Default theme for portal users.</p>
        </div>
      </div>

      {/* Behavior Settings */}
      <div style={{ ...sectionStyle, borderBottom: "none" }}>
        <h3 style={sectionTitleStyle}>Behavior Settings</h3>
        <p style={sectionDescStyle}>Configure portal behavior and user experience settings.</p>

        <div style={checkboxRowStyle}>
          <input
            type="checkbox"
            id="enableRequestSearch"
            checked={config.enableRequestSearch}
            onChange={(e) => handleChange({ enableRequestSearch: e.target.checked })}
            style={{ marginTop: "2px" }}
          />
          <label htmlFor="enableRequestSearch" style={{ fontSize: "14px", color: "#172b4d", cursor: "pointer" }}>
            Enable request type search in portals
          </label>
        </div>

        <div style={checkboxRowStyle}>
          <input
            type="checkbox"
            id="enableRecentPortals"
            checked={config.enableRecentPortals}
            onChange={(e) => handleChange({ enableRecentPortals: e.target.checked })}
            style={{ marginTop: "2px" }}
          />
          <label htmlFor="enableRecentPortals" style={{ fontSize: "14px", color: "#172b4d", cursor: "pointer" }}>
            Show recently visited portals for quick access
          </label>
        </div>

        {config.enableRecentPortals && (
          <div style={{ marginLeft: "24px", marginBottom: "12px" }}>
            <label style={labelStyle}>Max Recent Portals</label>
            <input
              type="number"
              min={1}
              max={20}
              value={config.maxRecentPortals}
              onChange={(e) => handleChange({ maxRecentPortals: parseInt(e.target.value, 10) || 5 })}
              style={{ ...inputStyle, width: "80px" }}
            />
            <p style={helpTextStyle}>Maximum number of recent portals to display (1-20).</p>
          </div>
        )}

        <div style={{ ...fieldGroupStyle, marginTop: "16px" }}>
          <label style={labelStyle}>Session Timeout (minutes)</label>
          <input
            type="number"
            min={5}
            max={480}
            value={config.sessionTimeoutMinutes}
            onChange={(e) => handleChange({ sessionTimeoutMinutes: parseInt(e.target.value, 10) || 30 })}
            style={{ ...inputStyle, width: "80px" }}
          />
          <p style={helpTextStyle}>Inactivity timeout before session expires (5-480 minutes).</p>
        </div>
      </div>
    </div>
  );
}

