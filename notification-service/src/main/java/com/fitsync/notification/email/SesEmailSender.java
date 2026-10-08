package com.fitsync.notification.email;

import java.nio.charset.StandardCharsets;

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
 */
public class SesEmailSender implements EmailSender {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private final SesV2Client client;
    private final String sender;

    public SesEmailSender(SesV2Client client, String sender) {
        this.client = client;
        this.sender = sender;
    }

    @Override
    public void send(OutgoingEmail message) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(sender)
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
