// Describe the API key this client authenticates with: its domain, organization, plan and
// send-quota usage. Nothing is sent, and the key itself is never printed.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/CredentialInfo.java
//
// The key is checked exactly as a send checks it, so this is also how to verify a key before
// using it: a key that cannot send raises AuthenticationException (invalid_api_key). The call
// counts as a use of the key, and plan figures can be up to 30 seconds old.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.AuthenticationException;
import com.mailkube.exception.MailkubeException;
import com.mailkube.model.ApiKey;
import com.mailkube.model.ApiKeyQuota;

void main() {
    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        ApiKey key = client.credential().info();
        System.out.println("key " + key.keyId() + " (" + key.name() + ") sends for " + key.domain().name());
        System.out.println("organization " + key.organization().name() + " (" + key.organization().alias() + ")");
        System.out.println("allowed from "
                + (key.allowedSourceIps().isEmpty() ? "any address" : String.join(", ", key.allowedSourceIps())));

        // A plan without a daily limit lists the monthly quota only; an absent quota is no limit.
        if (key.plan() == null) {
            System.out.println("no subscription");
            return;
        }
        System.out.println("plan " + key.plan().name() + " (" + key.plan().status() + ")");
        for (ApiKeyQuota quota : key.plan().quotas()) {
            System.out.println("  " + quota.name() + ": " + quota.used() + " of " + quota.limit()
                    + " recipients in " + quota.period());
        }
    } catch (AuthenticationException e) {
        System.err.println("this key cannot send: " + e.errorName());
        System.exit(1);
    } catch (MailkubeException e) {
        System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
        System.exit(1);
    }
}
