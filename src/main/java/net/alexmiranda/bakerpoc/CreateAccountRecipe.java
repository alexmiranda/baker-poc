package net.alexmiranda.bakerpoc;

import static com.ing.baker.recipe.javadsl.InteractionDescriptor.of;

import com.ing.baker.recipe.annotations.FiresEvent;
import com.ing.baker.recipe.annotations.RequiresIngredient;
import com.ing.baker.recipe.common.InteractionDescriptor;
import com.ing.baker.recipe.javadsl.Interaction;
import com.ing.baker.recipe.javadsl.Recipe;

import lombok.Builder;
import lombok.Data;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.CreateAccount.CreateAccountFailed;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.CreateAccount.CreateAccountSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.CreateCustomer.CreateCustomerSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.IssueDebitCard.IssueDebitCardFailed;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.IssueDebitCard.IssueDebitCardSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.LinkCaseDocuments.LinkCaseDocumentsFailed;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.LinkCaseDocuments.LinkCaseDocumentsSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.NotifyCustomer.NotifyCustomerFailed;

public class CreateAccountRecipe {
    public static final Recipe RECIPE = new Recipe(CreateAccountRecipe.class.getSimpleName())
            .withSensoryEvent(NewAccountRequested.class)
            .withInteraction(createCustomer())
            .withInteraction(createAccount())
            .withInteraction(issueDebitCard())
            .withInteraction(linkCaseDocuments())
            .withInteraction(notifyCustomer())
            .withInteraction(deleteCustomer())
            .withInteraction(deleteAccount());

    private static InteractionDescriptor createCustomer() {
        return of(CreateCustomer.class);
    }

    private static InteractionDescriptor createAccount() {
        return of(CreateAccount.class).withRequiredEvent(CreateCustomerSuccess.class);
    }

    private static InteractionDescriptor issueDebitCard() {
        return of(IssueDebitCard.class).withRequiredEvent(CreateAccountSuccess.class);
    }

    private static InteractionDescriptor linkCaseDocuments() {
        return of(LinkCaseDocuments.class)
                .withRequiredEvent(CreateAccountSuccess.class);
    }

    private static InteractionDescriptor notifyCustomer() {
        return of(NotifyCustomer.class)
                .withRequiredEvents(IssueDebitCardSuccess.class, LinkCaseDocumentsSuccess.class);
    }

    private static InteractionDescriptor deleteCustomer() {
        return of(DeleteCustomer.class)
                .withRequiredOneOfEvents(
                        CreateAccountFailed.class,
                        IssueDebitCardFailed.class,
                        LinkCaseDocumentsFailed.class,
                        NotifyCustomerFailed.class);
    }

    private static InteractionDescriptor deleteAccount() {
        return of(DeleteAccount.class)
                .withRequiredOneOfEvents(
                        IssueDebitCardFailed.class,
                        LinkCaseDocumentsFailed.class,
                        NotifyCustomerFailed.class);
    }

    @Data
    public static class NewAccountRequested {
        private final String caseId;
        private final CustomerData customerData;
        private final boolean notifyCustomer;
    }

    @Data
    @Builder
    static class CustomerData {
        private final String firstName;
        private final String lastName;
        private final String dateOfBirth;
        private final String email;
    }

    @Data
    static class AccountData {
        private final String accountNumber;
    }

    enum CardNetwork {
        VISA,
        MASTERCARD,
        AMERICAN_EXPRESS,
    }

    @Data
    @Builder
    static class DebitCardData {
        private final String cardNumber;
        private final String cardholderName;
        private final int expiryMonth;
        private final int expiryYear;
        private final String issuingBankCode;
        private final CardNetwork network;
        private final String variant;
    }

    public interface CreateCustomer extends Interaction {
        interface CreateCustomerOutcome {
        }

        @Data
        static class CreateCustomerSuccess implements CreateCustomerOutcome {
            private final String customerId;
        }

        @Data
        static class CreateCustomerFailed implements CreateCustomerOutcome {
        }

        @FiresEvent(oneOf = { CreateCustomerSuccess.class, CreateCustomerFailed.class })
        CreateCustomerOutcome apply(@RequiresIngredient("caseId") String caseId,
                @RequiresIngredient("customerData") CustomerData customer);
    }

    public interface CreateAccount extends Interaction {
        interface CreateAccountOutcome {
        }

        @Data
        static class CreateAccountSuccess implements CreateAccountOutcome {
            private final String accountId;
            private final AccountData accountData;
        }

        @Data
        static class CreateAccountFailed implements CreateAccountOutcome {
        }

        @FiresEvent(oneOf = { CreateAccountSuccess.class, CreateAccountFailed.class })
        CreateAccountOutcome apply(@RequiresIngredient("caseId") String caseId,
                @RequiresIngredient("customerId") String customerId);
    }

    public interface IssueDebitCard extends Interaction {
        interface IssueDebitCardOutcome {
        }

        @Data
        static class IssueDebitCardSuccess implements IssueDebitCardOutcome {
            private final DebitCardData debitCardData;
        }

        static class IssueDebitCardFailed implements IssueDebitCardOutcome {
        }

        @FiresEvent(oneOf = { IssueDebitCardSuccess.class, IssueDebitCardFailed.class })
        IssueDebitCardOutcome apply(@RequiresIngredient("caseId") String caseId,
                @RequiresIngredient("customerId") String customerId,
                @RequiresIngredient("customerData") CustomerData customerData);
    }

    public interface LinkCaseDocuments extends Interaction {
        interface LinkCaseDocumentsOutcome {
        }

        static class LinkCaseDocumentsSuccess implements LinkCaseDocumentsOutcome {
        }

        static class LinkCaseDocumentsFailed implements LinkCaseDocumentsOutcome {
        }

        @FiresEvent(oneOf = { LinkCaseDocumentsSuccess.class, LinkCaseDocumentsFailed.class })
        LinkCaseDocumentsOutcome apply(@RequiresIngredient("caseId") String caseId,
                @RequiresIngredient("accountId") String accountId);
    }

    public interface NotifyCustomer extends Interaction {
        interface NotifyCustomerOutcome {
        }

        @Data
        static class NotifyCustomerSuccess implements NotifyCustomerOutcome {
            private final boolean customerNotificationSkipped;
        }

        static class NotifyCustomerFailed implements NotifyCustomerOutcome {
        }

        @FiresEvent(oneOf = { NotifyCustomerSuccess.class, NotifyCustomerFailed.class })
        NotifyCustomerOutcome apply(@RequiresIngredient("notifyCustomer") boolean notifyCustomer,
                @RequiresIngredient("customerData") CustomerData customerData,
                @RequiresIngredient("accountData") AccountData accountData,
                @RequiresIngredient("debitCardData") DebitCardData debitCardData);
    }

    public interface DeleteCustomer extends Interaction {
        interface DeleteCustomerOutcome {
        }

        static class DeleteCustomerSuccess implements DeleteCustomerOutcome {
        }

        static class DeleteCustomerFailed implements DeleteCustomerOutcome {
        }

        @FiresEvent(oneOf = { DeleteCustomerSuccess.class, DeleteCustomerFailed.class })
        DeleteCustomerOutcome apply(@RequiresIngredient("customerId") String customerId);
    }

    public interface DeleteAccount extends Interaction {
        interface DeleteAccountOutcome {
        }

        static class DeleteAccountSuccess implements DeleteAccountOutcome {
        }

        static class DeleteAccountFailed implements DeleteAccountOutcome {
        }

        @FiresEvent(oneOf = { DeleteAccountSuccess.class, DeleteAccountFailed.class })
        DeleteAccountOutcome apply(@RequiresIngredient("accountId") String accountId);
    }
}