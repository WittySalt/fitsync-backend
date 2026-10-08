package com.fitsync.notification.email;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Sends through Amazon SES v2. The client takes its credentials from the SDK's default chain,
 * which is the EC2 instance role in the cloud, so no key is configured anywhere. Any SDK failure
 * is left to propagate, and the dispatcher counts it as a failed attempt.
 *
 * <p>The From header carries a display name ({@code FitSync <noreply@fitsyncapp.click>}). The IAM
 * condition {@code ses:FromAddress} is evaluated against the bare address, and the display name has
 * its own key, {@code ses:FromDisplayName}, so the name does not affect the send permission.
 */
public class SesEmailSender implements EmailSender {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    /** RFC 5322 atext plus the space: a name made only of these can stand unquoted. */
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~ -]+");
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7E]*");

    private final SesV2Client client;
    private final String from;

    public SesEmailSender(SesV2Client client, String sender) {
        this(client, sender, null);
    }

    /**
     * @param sender     the bare From address, an identity verified in SES
     * @param senderName the display name shown beside it; blank sends the bare address
     */
    public SesEmailSender(SesV2Client client, String sender, String senderName) {
        this.client = client;
        this.from = fromHeader(sender, senderName);
    }

    /**
     * Builds the From value per RFC 5322 section 3.4. A plain name stays as it is, any other ASCII
     * name becomes a quoted-string with backslash escapes, and a name with non-ASCII characters
     * becomes an RFC 2047 encoded-word, which is what SES asks for. Line breaks are removed first so
     * the name can never start another header.
     */
    static String fromHeader(String address, String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\r\n\t]+", " ").trim();
        if (cleaned.isEmpty()) {
            return address;
        }
        // Every form is built from the cleaned name before any check, so nothing is altered after
        // the checks pick one.
        String quoted = '"' + cleaned.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        String encoded = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(cleaned.getBytes(StandardCharsets.UTF_8))
                + "?=";
        String display;
        if (PLAIN_NAME.matcher(cleaned).matches()) {
            display = cleaned;
        } else if (PRINTABLE_ASCII.matcher(cleaned).matches()) {
            display = quoted;
        } else {
            display = encoded;
        }
        return display + " <" + address + ">";
    }

    @Override
    public void send(OutgoingEmail message) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(message.to()).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(content(message.subject()))
                                .body(Body.builder()
                                        .text(content(message.bodyText()))
                                        .html(content(EmailHtml.fromText(message.bodyText())))
                                        .build())
                                .build())
                        .build())
                .build();
        client.sendEmail(request);
    }

    private static Content content(String data) {
        return Content.builder().charset(UTF_8).data(data).build();
    }
}
