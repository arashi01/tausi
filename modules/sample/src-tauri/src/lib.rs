//! Tausi Sample - Customer Survey Application
//!
//! A real-world example demonstrating Tausi patterns for Tauri integration:
//!
//! - Custom command with typed request/response (save_survey)
//! - File system persistence via Tauri's app data directory
//! - Clean Rust backend matching Scala frontend models

use serde::{Deserialize, Serialize};
use std::fs;
use tauri::{AppHandle, Manager};

// ============================================================================
// Data Models (matching Scala models with Codec derivation)
// ============================================================================

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ContactDetails {
    first_name: String,
    last_name: String,
    email: String,
    company: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SurveyAnswers {
    satisfaction: Option<i32>,
    recommendation: Option<i32>,
    features: Vec<String>,
    feedback: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SurveySubmission {
    contact_details: ContactDetails,
    answers: SurveyAnswers,
    submitted_at: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct SaveSurveyResponse {
    file_path: String,
    message: String,
}

// ============================================================================
// Commands
// ============================================================================

/// Save a completed survey to the app's data directory.
///
/// This command demonstrates real Tauri backend integration:
/// - Receives typed data from Scala frontend (via Codec serialization)
/// - Writes to the platform-specific app data directory
/// - Returns structured response
///
/// Scala frontend defines this as:
/// ```scala
/// given saveSurvey: Command[SurveySubmission, SaveSurveyResponse] =
///   Command.define("save_survey")
/// ```
#[tauri::command]
fn save_survey(app: AppHandle, submission: SurveySubmission) -> Result<SaveSurveyResponse, String> {
    // Get the app data directory (platform-specific)
    let app_data_dir = app
        .path()
        .app_data_dir()
        .map_err(|e| format!("Failed to get app data directory: {}", e))?;

    // Create surveys subdirectory
    let surveys_dir = app_data_dir.join("surveys");
    fs::create_dir_all(&surveys_dir)
        .map_err(|e| format!("Failed to create surveys directory: {}", e))?;

    // Generate filename from last name and timestamp
    let safe_name = submission
        .contact_details
        .last_name
        .chars()
        .filter(|c| c.is_alphanumeric())
        .collect::<String>()
        .to_lowercase();
    let timestamp = chrono::Utc::now().format("%Y%m%d_%H%M%S");
    let filename = format!("survey_{}_{}.txt", safe_name, timestamp);
    let file_path = surveys_dir.join(&filename);

    // Format survey content
    let content = format_survey(&submission);

    // Write to file
    fs::write(&file_path, content)
        .map_err(|e| format!("Failed to write survey file: {}", e))?;

    let path_str = file_path.to_string_lossy().to_string();

    Ok(SaveSurveyResponse {
        file_path: path_str,
        message: "Thank you! Your survey has been saved.".to_string(),
    })
}

/// Format survey submission as human-readable text.
fn format_survey(submission: &SurveySubmission) -> String {
    let mut lines = Vec::new();

    lines.push("=".repeat(60));
    lines.push("CUSTOMER SURVEY SUBMISSION".to_string());
    lines.push("=".repeat(60));
    lines.push(String::new());

    lines.push("CONTACT INFORMATION".to_string());
    lines.push("-".repeat(40));
    lines.push(format!(
        "Name: {} {}",
        submission.contact_details.first_name, submission.contact_details.last_name
    ));
    lines.push(format!("Email: {}", submission.contact_details.email));
    if !submission.contact_details.company.is_empty() {
        lines.push(format!("Company: {}", submission.contact_details.company));
    }
    lines.push(String::new());

    lines.push("RATINGS".to_string());
    lines.push("-".repeat(40));
    if let Some(sat) = submission.answers.satisfaction {
        lines.push(format!("Overall Satisfaction: {}/5", sat));
    }
    if let Some(rec) = submission.answers.recommendation {
        lines.push(format!("Recommendation Likelihood: {}/5", rec));
    }
    lines.push(String::new());

    if !submission.answers.features.is_empty() {
        lines.push("IMPORTANT FEATURES".to_string());
        lines.push("-".repeat(40));
        for feature in &submission.answers.features {
            lines.push(format!("• {}", feature));
        }
        lines.push(String::new());
    }

    if !submission.answers.feedback.is_empty() {
        lines.push("ADDITIONAL FEEDBACK".to_string());
        lines.push("-".repeat(40));
        lines.push(submission.answers.feedback.clone());
        lines.push(String::new());
    }

    lines.push("=".repeat(60));
    lines.push(format!("Submitted: {}", submission.submitted_at));
    lines.push("=".repeat(60));

    lines.join("\n")
}

// ============================================================================
// Application Entry Point
// ============================================================================

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![save_survey])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
