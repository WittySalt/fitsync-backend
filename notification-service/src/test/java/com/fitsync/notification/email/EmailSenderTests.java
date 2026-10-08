package com.fitsync.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fitsync.notification.domain.EmailKind;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

class EmailSenderTests {

    private final OutgoingEmail email = new OutgoingEmail(UUID.randomUUID(), EmailKind.TRAINING_TODAY,
            "ada@example.com", "Today: <b>Legs</b>", "Hi Ada & co,\n\nSession: <script>x</script>\n\n"
                    + "Open today's session:\nhttps://app.fitsync.test/workout\n");

    @Test
    void htmlEscapesEveryValueAndLinksBareUrls() {
        String html = EmailHtml.fromText(email.bodyText());

        assertThat(html)
                .contains("<p>Hi Ada &amp; co,</p>")
                .contains("Session: &lt;script&gt;x&lt;/script&gt;")
                .doesNotContain("<script>")
                .contains("<a href=\"https://app.fitsync.test/workout\">https://app.fitsync.test/workout</a>");
    }

    @Test
    void sesSenderSendsTextAndHtmlFromTheConfiguredSender() {
        SesV2Client client = mock(SesV2Client.class);
        when(client.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().build());

        new SesEmailSender(client, "no-reply@fitsync.app").send(email);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();
        assertThat(request.fromEmailAddress()).isEqualTo("no-reply@fitsync.app");
        assertThat(request.destination().toAddresses()).containsExactly("ada@example.com");
        assertThat(request.content().simple().subject().data()).isEqualTo("Today: <b>Legs</b>");
        assertThat(request.content().simple().body().text().data()).isEqualTo(email.bodyText());
        assertThat(request.content().simple().body().html().data()).contains("&lt;script&gt;");
    }

    @Test
    void sesFailuresReachTheCaller() {
        SesV2Client client = mock(SesV2Client.class);
        when(client.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesV2Exception.builder().message("throttled").build());

        assertThatThrownBy(() -> new SesEmailSender(client, "no-reply@fitsync.app").send(email))
                .isInstanceOf(SesV2Exception.class);
    }

    @Test
    void neitherTheLoggingSenderNorTheRecordPrintsTheAddressOrBody() {
        new LoggingEmailSender().send(email);
        assertThat(email.toString()).doesNotContain("ada@example.com").doesNotContain("Session");
    }
}
