// Learn more about Tauri commands at https://tauri.app/develop/calling-rust/
use serde::{Deserialize, Serialize};
use std::fs;
use std::io::Write;
use std::thread;
use std::time::Duration;
use tauri::{AppHandle, Emitter, Listener, Manager};

#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
}

// === Survey Types ===

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ContactDetails {
    first_name: String,
    last_name: String,
    phone_number: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SurveySubmission {
    contact_details: ContactDetails,
    answers: std::collections::HashMap<String, String>,
    submitted_at: String,
}

#[tauri::command]
fn save_survey(app: AppHandle, submission: SurveySubmission) -> Result<(), String> {
    // Get app data directory
    let app_data_dir = app
        .path()
        .app_data_dir()
        .map_err(|e| format!("Failed to get app data directory: {}", e))?;
    
    // Create surveys subdirectory if it doesn't exist
    let surveys_dir = app_data_dir.join("surveys");
    fs::create_dir_all(&surveys_dir)
        .map_err(|e| format!("Failed to create surveys directory: {}", e))?;
    
    // Generate filename with timestamp
    let timestamp = chrono::Utc::now().format("%Y%m%d_%H%M%S").to_string();
    let filename = format!(
        "survey_{}_{}.txt",
        submission.contact_details.last_name.to_lowercase().replace(" ", "_"),
        timestamp
    );
    let filepath = surveys_dir.join(&filename);
    
    // Format survey content
    let content = format_survey_content(&submission);
    
    // Write to file
    let mut file = fs::File::create(&filepath)
        .map_err(|e| format!("Failed to create survey file: {}", e))?;
    file.write_all(content.as_bytes())
        .map_err(|e| format!("Failed to write survey content: {}", e))?;
    
    println!("Survey saved to: {:?}", filepath);
    
    Ok(())
}

fn format_survey_content(submission: &SurveySubmission) -> String {
    let contact = &submission.contact_details;
    let mut content = String::new();
    
    content.push_str("=====================================\n");
    content.push_str("     CUSTOMER SATISFACTION SURVEY    \n");
    content.push_str("=====================================\n\n");
    
    content.push_str(&format!("Submitted: {}\n\n", submission.submitted_at));
    
    content.push_str("--- Contact Information ---\n");
    content.push_str(&format!("Name: {} {}\n", contact.first_name, contact.last_name));
    content.push_str(&format!("Phone: {}\n\n", contact.phone_number));
    
    content.push_str("--- Survey Responses ---\n");
    
    // Sort answers by key for consistent output
    let mut sorted_answers: Vec<_> = submission.answers.iter().collect();
    sorted_answers.sort_by_key(|(k, _)| k.as_str());
    
    for (question_id, answer) in sorted_answers {
        let question_label = format_question_label(question_id);
        content.push_str(&format!("\n{}\n", question_label));
        content.push_str(&format!("Answer: {}\n", answer));
    }
    
    content.push_str("\n=====================================\n");
    content.push_str("        Thank you for your feedback!  \n");
    content.push_str("=====================================\n");
    
    content
}

fn format_question_label(question_id: &str) -> String {
    match question_id {
        "overall_satisfaction" => "Overall Satisfaction (1-5)".to_string(),
        "recommendation_likelihood" => "Likelihood to Recommend (1-5)".to_string(),
        "service_quality" => "Service Quality Rating (1-5)".to_string(),
        "communication_rating" => "Communication Rating (1-5)".to_string(),
        "industry" => "Industry".to_string(),
        "product_line" => "Product Line".to_string(),
        "feedback_topic" => "Feedback Focus Area".to_string(),
        "best_aspect" => "What do you like most?".to_string(),
        "improvement_suggestion" => "What could we do better?".to_string(),
        "additional_feedback" => "Additional Comments".to_string(),
        _ => question_id.replace("_", " ").to_string(),
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct CounterUpdate {
    count: i32,
    timestamp: u64,
}

#[tauri::command]
async fn start_counter(app: AppHandle, max: i32) -> Result<String, String> {
    let app_clone = app.clone();

    thread::spawn(move || {
        for i in 1..=max {
            let timestamp = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs();

            let update = CounterUpdate {
                count: i,
                timestamp,
            };

            app_clone.emit("counter-update", &update).ok();
            thread::sleep(Duration::from_millis(500));
        }

        app_clone.emit("counter-finished", ()).ok();
    });

    Ok(format!("Counter started with max value: {}", max))
}

#[derive(Deserialize)]
struct EchoPayload {
    message: String,
}

#[tauri::command]
fn echo(payload: EchoPayload) -> Result<String, String> {
    if payload.message.is_empty() {
        Err("Message cannot be empty".to_string())
    } else {
        Ok(format!("Echo: {}", payload.message))
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![greet, start_counter, echo, save_survey])
        .setup(|app| {
            // Listen for frontend events
            let app_handle = app.handle().clone();
            app.listen("frontend-ready", move |_event| {
                println!("Frontend is ready!");
                app_handle
                    .emit("backend-ready", "Backend initialized successfully")
                    .ok();
            });
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
