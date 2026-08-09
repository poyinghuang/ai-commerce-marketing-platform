package com.aicommerce.platform.connector.drive.infrastructure.provider;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import com.aicommerce.platform.connector.drive.application.*;
import com.google.auth.oauth2.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleDriveStorageProviderTest {
    @Test void failsClosedWithoutConfiguredRoot(){var provider=new GoogleDriveStorageProvider(RestClient.create(),credentials(),"","");
        assertThatThrownBy(()->provider.ensureProductTree(new StorageEnsureRequest(UUID.randomUUID(),"PROD-00000001")))
                .isInstanceOfSatisfying(StorageProviderException.class,e->assertThat(e.getCode()).isEqualTo("CONNECTOR_NOT_CONFIGURED"));}
    @Test void sharedDriveSearchUsesFixedOriginAndRequiredParametersAndReusesFolders(){
        RestClient.Builder builder=RestClient.builder().baseUrl("https://www.googleapis.com");MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        for(int i=0;i<7;i++){String id="folder-id-"+i;server.expect(once(),request->{var uri=request.getURI();assertThat(uri.getHost()).isEqualTo("www.googleapis.com");assertThat(uri.getPath()).isEqualTo("/drive/v3/files");
                    assertThat(uri.getQuery()).contains("supportsAllDrives=true","includeItemsFromAllDrives=true","corpora=drive","driveId=shared-drive");})
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION,"Bearer token-value"))
                .andRespond(withSuccess("{\"files\":[{\"id\":\""+id+"\"}]}",MediaType.APPLICATION_JSON));}
        var provider=new GoogleDriveStorageProvider(builder.build(),credentials(),"root-folder","shared-drive");
        var tree=provider.ensureProductTree(new StorageEnsureRequest(UUID.randomUUID(),"PROD-00000001"));
        assertThat(tree.rootFolderId()).isEqualTo("root-folder");assertThat(tree.sharedDriveId()).isEqualTo("shared-drive");assertThat(tree.subfolderIds()).hasSize(6);server.verify();
    }
    @Test void myDriveCreatesTheManagedTreeWithoutSharedDriveParameters(){
        RestClient.Builder builder=RestClient.builder().baseUrl("https://www.googleapis.com");
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        UUID productUuid=UUID.fromString("12345678-1234-1234-1234-123456789abc");
        for(int i=0;i<7;i++){
            int folderIndex=i;
            server.expect(once(),request->{String query=request.getURI().getQuery();assertThat(query).doesNotContain("supportsAllDrives","driveId","corpora");})
                    .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"files\":[]}",MediaType.APPLICATION_JSON));
            server.expect(once(),request->{String query=request.getURI().getQuery();assertThat(query).doesNotContain("supportsAllDrives","driveId");})
                    .andExpect(method(HttpMethod.POST)).andExpect(content().string(containsString(productUuid.toString())))
                    .andRespond(withSuccess("{\"id\":\"created-folder-"+folderIndex+"\"}",MediaType.APPLICATION_JSON));
        }
        var provider=new GoogleDriveStorageProvider(builder.build(),credentials(),"root-folder","");
        var tree=provider.ensureProductTree(new StorageEnsureRequest(productUuid,"PROD-00000001"));
        assertThat(tree.productFolderId()).isEqualTo("created-folder-0");
        assertThat(tree.subfolderIds()).hasSize(6);server.verify();
    }
    @Test void retriesTransientResponsesAndDoesNotExposeProviderPayload(){
        RestClient.Builder retryBuilder=RestClient.builder().baseUrl("https://www.googleapis.com");
        MockRestServiceServer retryServer=MockRestServiceServer.bindTo(retryBuilder).build();
        retryServer.expect(once(),method(HttpMethod.GET)).andRespond(withServerError());
        for(int i=0;i<7;i++)retryServer.expect(once(),method(HttpMethod.GET))
                .andRespond(withSuccess("{\"files\":[{\"id\":\"existing-folder-"+i+"\"}]}",MediaType.APPLICATION_JSON));
        var retrying=new GoogleDriveStorageProvider(retryBuilder.build(),credentials(),"root-folder","");
        assertThat(retrying.ensureProductTree(new StorageEnsureRequest(UUID.randomUUID(),"PROD-00000001")).subfolderIds()).hasSize(6);
        retryServer.verify();

        RestClient.Builder deniedBuilder=RestClient.builder().baseUrl("https://www.googleapis.com");
        MockRestServiceServer deniedServer=MockRestServiceServer.bindTo(deniedBuilder).build();
        deniedServer.expect(once(),method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("credential=secret-provider-payload").contentType(MediaType.TEXT_PLAIN));
        var denied=new GoogleDriveStorageProvider(deniedBuilder.build(),credentials(),"root-folder","");
        assertThatThrownBy(()->denied.ensureProductTree(new StorageEnsureRequest(UUID.randomUUID(),"PROD-00000001")))
                .isInstanceOfSatisfying(StorageProviderException.class,e->{
                    assertThat(e.getCode()).isEqualTo("GOOGLE_PERMISSION_DENIED");
                    assertThat(e.getMessage()).doesNotContain("secret-provider-payload","credential");
                });
        deniedServer.verify();
    }
    private GoogleDriveStorageProvider.CredentialsSource credentials(){return ()->GoogleCredentials.create(new AccessToken("token-value",new Date(System.currentTimeMillis()+3600000)));}
}
