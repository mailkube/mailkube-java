// Runnable documentation: the whole scheduled-email lifecycle, end to end.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/ManageScheduledEmails.java you@example.com
//
// It schedules two sends under one batch, lists them, reschedules one, walks the whole collection,
// then cancels the batch — so nothing this script creates is actually delivered.
//
// Examples are excluded from lint, coverage and the duplication gate: they exist to be read and
// run, not to be shipped. Gradle never compiles this directory.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.ApiException;
import com.mailkube.exception.ErrorName;
import com.mailkube.exception.InvalidRequestException;
import com.mailkube.exception.NotFoundException;
import com.mailkube.model.Email;
import com.mailkube.model.ScheduledEmail;
import com.mailkube.model.ScheduledEmailListParams;
import com.mailkube.model.ScheduledEmailPage;
import com.mailkube.model.ScheduledEmailUpdateParams;
import com.mailkube.model.SendEmailParams;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/ManageScheduledEmails.java <recipient@example.com>");
        System.exit(2);
    }

    String recipient = args[0];
    String batchId = "example-" + System.currentTimeMillis();
    Instant due = Instant.now().plus(Duration.ofHours(2));

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY

        // 1. Schedule. A send carrying scheduledAt is accepted now and delivered later; batchId is
        //    a label you choose, and it only means anything alongside scheduledAt.
        Email first = client.emails()
                .send(SendEmailParams.builder(
                                "Acme <hello@yourdomain.com>", List.of(recipient), "Your weekly digest")
                        .html("<p>Here's what happened.</p>")
                        .scheduledAt(due)
                        .batchId(batchId)
                        .build());

        client.emails()
                .send(SendEmailParams.builder("Acme <hello@yourdomain.com>", List.of(recipient), "One more thing")
                        .html("<p>And this too.</p>")
                        .scheduledAt(due)
                        .batchId(batchId)
                        .build());

        System.out.println("scheduled " + first.id() + " for " + first.scheduledAt());
        System.out.println("isScheduled: " + first.isScheduled() + ", status: " + first.status());

        // 2. Retrieve one. Timestamps come back as the verbatim strings the server sent, and
        //    `recipients` is a summary ("a@b.com +2"), not the full list.
        ScheduledEmail fetched = client.scheduledEmails().get(first.id());
        System.out.println("subject: " + fetched.subject() + ", recipients: " + fetched.recipients());

        // 3. List one page, filtered.
        ScheduledEmailPage page = client.scheduledEmails()
                .list(ScheduledEmailListParams.builder()
                        .status("scheduled")
                        .batchId(batchId)
                        .build());
        System.out.println("page holds " + page.data().size() + " of " + page.pagination().totalCount()
                + ", more pages: " + page.hasMore());

        // 4. Reschedule one of them, and move it out of the batch while we are at it. The
        //    single-item route takes both; the batch route below takes only a due time.
        ScheduledEmail moved = client.scheduledEmails()
                .update(
                        first.id(),
                        ScheduledEmailUpdateParams.builder(due.plus(Duration.ofHours(1)))
                                .batchId("example-moved")
                                .build());
        System.out.println("moved to " + moved.scheduledAt() + " in batch " + moved.batchId());

        // 5. Walk everything still pending. Pages are fetched lazily by following the server's own
        //    links, so `limit` here costs exactly one request no matter how large the collection is.
        client.scheduledEmails()
                .iterateAll(ScheduledEmailListParams.builder().status("scheduled").build())
                .limit(10)
                .forEach(email -> System.out.println("  " + email.id() + "  " + email.scheduledAt() + "  "
                        + email.subject()));

        // 6. Clean up. Cancelling a batch cancels everything still pending under that label; an
        //    unknown batch is a no-op reporting 0 rather than an error.
        System.out.println(
                "cancelled " + client.scheduledEmails().batches().cancel(batchId).canceledCount() + " from " + batchId);
        System.out.println("cancelled " + client.scheduledEmails().cancel(moved.id()).status());

    } catch (NotFoundException e) {
        // scheduled_email_not_found: already gone, or never existed.
        System.err.println("no such scheduled email: " + e.getMessage());
        System.exit(1);
    } catch (InvalidRequestException e) {
        if (ErrorName.SCHEDULED_EMAIL_NOT_PENDING.equals(e.errorName())) {
            System.err.println("too late: it has already been sent or cancelled");
        } else {
            System.err.println("rejected: " + e.errorName() + " " + e.getMessage());
        }
        System.exit(1);
    } catch (ApiException e) {
        // Quote requestId when contacting support.
        System.err.println(e.statusCode() + " " + e.errorName() + ": " + e.getMessage() + " (request " + e.requestId()
                + ")");
        System.exit(1);
    }
}
