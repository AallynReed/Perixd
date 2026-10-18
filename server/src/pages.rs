use axum::response::Html;

const STYLE: &str = include_str!("../pages/style.css");
const DELETE_SCRIPT: &str = include_str!("../pages/delete.js");
pub const INDEX: &str = include_str!("../pages/index.html");
pub const PRIVACY_EN: &str = include_str!("../pages/privacy-en.html");
pub const PRIVACY_PT: &str = include_str!("../pages/privacy-pt.html");
pub const DELETE_EN: &str = include_str!("../pages/delete-en.html");
pub const DELETE_PT: &str = include_str!("../pages/delete-pt.html");

/// Fills in the shared style and script and the contact, which is an email address or a URL.
pub fn render(template: &str, contact: &str) -> Html<String> {
    let contact = escape(contact);
    let href = if contact.contains('@') {
        format!("mailto:{contact}")
    } else {
        contact.clone()
    };
    Html(
        template
            .replace("{{STYLE}}", STYLE)
            .replace("{{DELETE_SCRIPT}}", DELETE_SCRIPT)
            .replace("{{CONTACT_HREF}}", &href)
            .replace("{{CONTACT}}", &contact),
    )
}

fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
}
