package net.alexmiranda.bakerpoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static net.alexmiranda.bakerpoc.CreateAccountRecipe.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ing.baker.compiler.RecipeCompiler;
import com.ing.baker.il.CompiledRecipe;
import com.ing.baker.runtime.javadsl.Baker;
import com.ing.baker.runtime.inmemory.InMemoryBaker;
import com.ing.baker.runtime.javadsl.EventInstance;

import net.alexmiranda.bakerpoc.CreateAccountRecipe.CreateAccount.CreateAccountFailed;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.CreateAccount.CreateAccountSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.CreateCustomer.CreateCustomerSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.DeleteAccount.DeleteAccountSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.DeleteCustomer.DeleteCustomerSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.DebitCardData;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.IssueDebitCard.IssueDebitCardFailed;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.IssueDebitCard.IssueDebitCardSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.LinkCaseDocuments.LinkCaseDocumentsFailed;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.LinkCaseDocuments.LinkCaseDocumentsSuccess;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.NotifyCustomer.NotifyCustomerFailed;
import net.alexmiranda.bakerpoc.CreateAccountRecipe.NotifyCustomer.NotifyCustomerSuccess;

@ExtendWith(MockitoExtension.class)
public class CreateAccountRecipeTest {
        private static final String testCaseId = "testCaseId";
        private static final String testCustomerId = "testCustomerId";
        private static final String testAccountId = "testAccountId";

        private static CustomerData customerData;
        private static AccountData accountData;
        private static CompiledRecipe recipe = null;
        private Baker baker;
        private String recipeId;

        @Mock
        CreateCustomer createCustomer;

        @Mock
        CreateAccount createAccount;

        @Mock
        IssueDebitCard issueDebitCard;

        @Mock
        LinkCaseDocuments linkCaseDocuments;

        @Mock
        NotifyCustomer notifyCustomer;

        @Mock
        DeleteCustomer deleteCustomer;

        @Mock
        DeleteAccount deleteAccount;

        @BeforeAll
        public static void beforeAll() {
                recipe = RecipeCompiler.compileRecipe(CreateAccountRecipe.RECIPE);
                customerData = CustomerData.builder()
                                .firstName("firstName")
                                .lastName("lastName")
                                .dateOfBirth("01-01-1970")
                                .email("customer@example.com")
                                .build();
                accountData = new AccountData("testAccountNumber");
        }

        @BeforeEach
        public void beforeEach() throws InterruptedException, ExecutionException {
                baker = InMemoryBaker.java(
                                List.of(createCustomer,
                                                createAccount,
                                                issueDebitCard,
                                                linkCaseDocuments,
                                                notifyCustomer,
                                                deleteCustomer,
                                                deleteAccount));
                recipeId = baker.addRecipe(recipe, true).get();
        }

        @Test
        public void testVisualiseRecipe() {
                // use webgraphviz.com to visualise
                var dot = recipe.getRecipeVisualization();
                assertThat(dot).isNotBlank();
        }

        @Test
        public void testHappyFlow() throws InterruptedException, ExecutionException {
                var debitCardData = testDebitCard();

                var request = newAccountRequestEvent();
                when(createCustomer.apply(anyString(), any(CustomerData.class)))
                                .thenReturn(new CreateCustomerSuccess(testCustomerId));
                when(createAccount.apply(anyString(), anyString()))
                                .thenReturn(new CreateAccountSuccess(testAccountId, accountData));
                when(issueDebitCard.apply(anyString(), anyString(), any(CustomerData.class)))
                                .thenReturn(new IssueDebitCardSuccess(debitCardData));
                when(linkCaseDocuments.apply(anyString(), anyString()))
                                .thenReturn(new LinkCaseDocumentsSuccess());
                when(notifyCustomer.apply(
                                anyBoolean(),
                                any(CustomerData.class),
                                any(AccountData.class),
                                any(DebitCardData.class)))
                                .thenReturn(new NotifyCustomerSuccess(false));

                var inOrder = inOrder(createCustomer, createAccount, notifyCustomer);
                var recipeInstanceId = UUID.randomUUID().toString();

                @SuppressWarnings("null")
                var state = baker.bake(recipeId, recipeInstanceId)
                                .thenCompose(_ignore -> baker.fireEventAndResolveWhenCompleted(recipeInstanceId,
                                                request))
                                .thenCompose(_ignore -> baker.getRecipeInstanceState(recipeInstanceId)).get();

                assertThat(state.ingredients()).containsKeys(
                                "customerId",
                                "accountId",
                                "customerData",
                                "accountData",
                                "debitCardData",
                                "customerNotificationSkipped");

                assertThat(state.events()).extracting(event -> event.getName()).contains(
                                "CreateCustomerSuccess",
                                "CreateAccountSuccess",
                                "IssueDebitCardSuccess",
                                "LinkCaseDocumentsSuccess",
                                "NotifyCustomerSuccess");

                inOrder.verify(createCustomer, times(1))
                                .apply(eq(testCaseId), eq(customerData));
                inOrder.verify(createAccount, times(1))
                                .apply(eq(testCaseId), eq(testCustomerId));
                verify(issueDebitCard, times(1))
                                .apply(eq(testCaseId), eq(testCustomerId), eq(customerData));
                verify(linkCaseDocuments, times(1))
                                .apply(eq(testCaseId), eq(testAccountId));
                inOrder.verify(notifyCustomer, times(1))
                                .apply(eq(true), eq(customerData), eq(accountData), eq(debitCardData));
        }

        @Test
        public void testDeleteCustomerWhenCreateAccountFailed() throws InterruptedException, ExecutionException {
                var request = newAccountRequestEvent();
                when(createCustomer.apply(anyString(), any(CustomerData.class)))
                                .thenReturn(new CreateCustomerSuccess(testCustomerId));
                when(createAccount.apply(anyString(), anyString())).thenReturn(new CreateAccountFailed());
                when(deleteCustomer.apply(anyString())).thenReturn(new DeleteCustomerSuccess());
                var inOrder = inOrder(createCustomer, createAccount, deleteCustomer);

                var recipeInstanceId = UUID.randomUUID().toString();

                @SuppressWarnings("null")
                var state = baker.bake(recipeId, recipeInstanceId)
                                .thenCompose(_ignore -> baker.fireEventAndResolveWhenCompleted(recipeInstanceId,
                                                request))
                                .thenCompose(_ignore -> baker.getRecipeInstanceState(recipeInstanceId)).get();

                assertThat(state.ingredients()).containsKeys("customerData", "customerId");
                assertThat(state.ingredients()).doesNotContainKey("accountId");
                assertThat(state.events()).extracting(event -> event.getName())
                                .contains("CreateCustomerSuccess", "CreateAccountFailed", "DeleteCustomerSuccess");

                inOrder.verify(createCustomer, times(1)).apply(eq(testCaseId), eq(customerData));
                inOrder.verify(createAccount, times(1)).apply(eq(testCaseId), eq(testCustomerId));
                inOrder.verify(deleteCustomer, times(1)).apply(eq(testCustomerId));
        }

        @Test
        public void testRollbackWhenIssueDebitCardFailed() throws InterruptedException, ExecutionException {
                var request = newAccountRequestEvent();
                when(createCustomer.apply(anyString(), any(CustomerData.class)))
                                .thenReturn(new CreateCustomerSuccess(testCustomerId));
                when(createAccount.apply(anyString(), anyString()))
                                .thenReturn(new CreateAccountSuccess(testAccountId, accountData));
                when(issueDebitCard.apply(anyString(), anyString(), any(CustomerData.class)))
                                .thenReturn(new IssueDebitCardFailed());
                when(linkCaseDocuments.apply(anyString(), anyString()))
                                .thenReturn(new LinkCaseDocumentsSuccess());
                when(deleteCustomer.apply(anyString())).thenReturn(new DeleteCustomerSuccess());
                when(deleteAccount.apply(anyString())).thenReturn(new DeleteAccountSuccess());
                var inOrder = inOrder(createCustomer, createAccount, issueDebitCard);

                var recipeInstanceId = UUID.randomUUID().toString();

                @SuppressWarnings("null")
                var state = baker.bake(recipeId, recipeInstanceId)
                                .thenCompose(_ignore -> baker.fireEventAndResolveWhenCompleted(recipeInstanceId,
                                                request))
                                .thenCompose(_ignore -> baker.getRecipeInstanceState(recipeInstanceId)).get();

                assertThat(state.ingredients()).containsKeys("customerData", "customerId", "accountId");
                assertThat(state.ingredients()).doesNotContainKey("debitCardData");
                assertThat(state.events()).extracting(event -> event.getName()).contains(
                                "CreateCustomerSuccess",
                                "CreateAccountSuccess",
                                "IssueDebitCardFailed",
                                "LinkCaseDocumentsSuccess");

                inOrder.verify(createCustomer, times(1)).apply(eq(testCaseId), eq(customerData));
                inOrder.verify(createAccount, times(1)).apply(eq(testCaseId), eq(testCustomerId));
                inOrder.verify(issueDebitCard, times(1)).apply(eq(testCaseId), eq(testCustomerId), eq(customerData));
                verify(deleteCustomer, times(1)).apply(eq(testCustomerId));
                verify(deleteAccount, times(1)).apply(eq(testAccountId));
        }

        @Test
        public void testRollbackWhenLinkCaseDocumentsFailed() throws InterruptedException, ExecutionException {
                var request = newAccountRequestEvent();
                when(createCustomer.apply(anyString(), any(CustomerData.class)))
                                .thenReturn(new CreateCustomerSuccess(testCustomerId));
                when(createAccount.apply(anyString(), anyString()))
                                .thenReturn(new CreateAccountSuccess(testAccountId, accountData));
                when(issueDebitCard.apply(anyString(), anyString(), any(CustomerData.class)))
                                .thenReturn(new IssueDebitCardSuccess(testDebitCard()));
                when(linkCaseDocuments.apply(anyString(), anyString()))
                                .thenReturn(new LinkCaseDocumentsFailed());
                when(deleteCustomer.apply(anyString())).thenReturn(new DeleteCustomerSuccess());
                when(deleteAccount.apply(anyString())).thenReturn(new DeleteAccountSuccess());
                var inOrder = inOrder(createCustomer, createAccount, linkCaseDocuments);

                var recipeInstanceId = UUID.randomUUID().toString();

                @SuppressWarnings("null")
                var state = baker.bake(recipeId, recipeInstanceId)
                                .thenCompose(_ignore -> baker.fireEventAndResolveWhenCompleted(recipeInstanceId,
                                                request))
                                .thenCompose(_ignore -> baker.getRecipeInstanceState(recipeInstanceId)).get();

                assertThat(state.ingredients()).containsKeys("customerData", "customerId", "accountId",
                                "debitCardData");
                assertThat(state.events()).extracting(event -> event.getName()).contains(
                                "CreateCustomerSuccess",
                                "CreateAccountSuccess",
                                "IssueDebitCardSuccess",
                                "LinkCaseDocumentsFailed");

                inOrder.verify(createCustomer, times(1)).apply(eq(testCaseId), eq(customerData));
                inOrder.verify(createAccount, times(1)).apply(eq(testCaseId), eq(testCustomerId));
                inOrder.verify(linkCaseDocuments, times(1)).apply(eq(testCaseId), eq(testAccountId));
                verify(deleteCustomer, times(1)).apply(eq(testCustomerId));
                verify(deleteAccount, times(1)).apply(eq(testAccountId));
        }

        @Test
        public void testRollbackWhenNotifyCustomerFailed() throws InterruptedException, ExecutionException {
                var request = newAccountRequestEvent();
                var debitCardData = testDebitCard();

                when(createCustomer.apply(anyString(), any(CustomerData.class)))
                                .thenReturn(new CreateCustomerSuccess(testCustomerId));
                when(createAccount.apply(anyString(), anyString()))
                                .thenReturn(new CreateAccountSuccess(testAccountId, accountData));
                when(issueDebitCard.apply(anyString(), anyString(), any(CustomerData.class)))
                                .thenReturn(new IssueDebitCardSuccess(debitCardData));
                when(linkCaseDocuments.apply(anyString(), anyString()))
                                .thenReturn(new LinkCaseDocumentsSuccess());
                when(notifyCustomer.apply(anyBoolean(), any(CustomerData.class), any(AccountData.class),
                                any(DebitCardData.class))).thenReturn(new NotifyCustomerFailed());
                when(deleteCustomer.apply(anyString())).thenReturn(new DeleteCustomerSuccess());
                when(deleteAccount.apply(anyString())).thenReturn(new DeleteAccountSuccess());
                var inOrder = inOrder(createCustomer, createAccount, notifyCustomer);

                var recipeInstanceId = UUID.randomUUID().toString();

                @SuppressWarnings("null")
                var state = baker.bake(recipeId, recipeInstanceId)
                                .thenCompose(_ignore -> baker.fireEventAndResolveWhenCompleted(recipeInstanceId,
                                                request))
                                .thenCompose(_ignore -> baker.getRecipeInstanceState(recipeInstanceId)).get();

                assertThat(state.ingredients()).containsKeys(
                                "customerData",
                                "customerId",
                                "accountId",
                                "accountData",
                                "debitCardData");
                assertThat(state.events()).extracting(event -> event.getName()).contains(
                                "CreateCustomerSuccess",
                                "CreateAccountSuccess",
                                "IssueDebitCardSuccess",
                                "LinkCaseDocumentsSuccess",
                                "NotifyCustomerFailed");

                inOrder.verify(createCustomer, times(1)).apply(eq(testCaseId), eq(customerData));
                inOrder.verify(createAccount, times(1)).apply(eq(testCaseId), eq(testCustomerId));
                verify(issueDebitCard, times(1)).apply(eq(testCaseId), eq(testCustomerId), eq(customerData));
                verify(linkCaseDocuments, times(1)).apply(eq(testCaseId), eq(testAccountId));
                inOrder.verify(notifyCustomer, times(1)).apply(eq(true), eq(customerData), eq(accountData),
                                eq(debitCardData));
                verify(deleteCustomer, times(1)).apply(eq(testCustomerId));
                verify(deleteAccount, times(1)).apply(eq(testAccountId));
        }

        private DebitCardData testDebitCard() {
                return DebitCardData.builder()
                                .cardNumber("testCardNumber")
                                .cardholderName("Esteemed Customer")
                                .expiryMonth(12)
                                .expiryYear(2023)
                                .issuingBankCode("BANK")
                                .network(CardNetwork.VISA)
                                .variant("Regular")
                                .build();
        }

        private EventInstance newAccountRequestEvent() {
                var request = new NewAccountRequested("testCaseId", customerData, true);
                return EventInstance.from(request);
        }
}
