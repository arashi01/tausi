// Learn more about Tauri commands at https://tauri.app/develop/calling-rust/
use serde::{Deserialize, Serialize};
use std::thread;
use std::time::Duration;
use tauri::{AppHandle, Emitter, Listener};

#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
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
        .invoke_handler(tauri::generate_handler![greet, start_counter, echo])
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
