package com.fitsync.notification.email;

import org.springframework.web.util.HtmlUtils;

/**
 * Builds the HTML version of an email from its plain text. Every value is escaped first, so
 * nothing a user typed (a display name, a session title) can become markup. Blank lines separate
 * paragraphs, and a line that holds only a link becomes a clickable link.
 */
public final class EmailHtml {

    private EmailHtml() {
    }

    public static String fromText(String text) {
        StringBuilder html = new StringBuilder(
                "<!doctype html><html><body style=\"font-family:Arial,Helvetica,sans-serif;"
                        + "font-size:15px;line-height:1.5;color:#1f2937\">");
        for (String paragraph : text.split("\\R\\R+")) {
            html.append("<p>");
            String[] lines = paragraph.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    html.append("<br>");
                }
                html.append(line(lines[i].strip()));
            }
            html.append("</p>");
        }
        return html.append("</body></html>").toString();
    }

    private static String line(String line) {
        String escaped = HtmlUtils.htmlEscape(line);
        if (line.startsWith("https://") || line.startsWith("http://")) {
            return "<a href=\"" + escaped + "\">" + escaped + "</a>";
        }
        return escaped;
    }
}
