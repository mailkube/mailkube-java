// List the sends still waiting to go out: one page for the metadata, then the lazy walk for the
// rows. Read-only: nothing is scheduled, moved or canceled.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/ListScheduledEmails.java
//
// list() returns one page plus its pagination block. iterateAll() follows the links the server
// issues and fetches the next page only once the stream asks for it, so limit() below means the
// remaining pages are never requested at all.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.MailkubeException;
import com.mailkube.model.ScheduledEmail;
import com.mailkube.model.ScheduledEmailListParams;
import com.mailkube.model.ScheduledEmailPage;
import java.util.List;
import java.util.stream.Stream;

void main() {
    int shown = 20;
    ScheduledEmailListParams scheduled =
            ScheduledEmailListParams.builder().status(List.of("scheduled")).build();

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        ScheduledEmailPage first = client.scheduledEmails().list(scheduled);
        System.out.println("page " + first.pagination().currentPage() + ": " + first.data().size() + " of "
                + first.pagination().totalCount() + " scheduled, more: " + first.hasMore());

        try (Stream<ScheduledEmail> rows =
                client.scheduledEmails().iterateAll(scheduled).limit(shown)) {
            rows.forEach(email -> System.out.println(email.scheduledAt() + "  " + email.id() + "  "
                    + (email.batchId() == null ? "-" : email.batchId()) + "  " + email.subject()));
        }
        System.out.println("stopped after at most " + shown + "; later pages were never fetched");
    } catch (MailkubeException e) {
        System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
        System.exit(1);
    }
}
