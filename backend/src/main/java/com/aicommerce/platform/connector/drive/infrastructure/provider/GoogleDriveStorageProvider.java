package com.aicommerce.platform.connector.drive.infrastructure.provider;

import com.aicommerce.platform.connector.drive.application.*;
import com.aicommerce.platform.connector.drive.domain.StorageFolderRole;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
@Conditional(GoogleDriveStorageProviderCondition.class)
public class GoogleDriveStorageProvider implements StorageProvider {
    private static final String SCOPE="https://www.googleapis.com/auth/drive.file"; private static final int ATTEMPTS=2;
    private final RestClient client; private final CredentialsSource credentials; private final String rootFolderId; private final String sharedDriveId;
    @Autowired
    public GoogleDriveStorageProvider(@Value("${GOOGLE_DRIVE_ROOT_FOLDER_ID:}") String rootFolderId,
            @Value("${GOOGLE_SHARED_DRIVE_ID:}") String sharedDriveId){this(fixedClient(),GoogleDriveStorageProvider::adc,rootFolderId,sharedDriveId);}
    GoogleDriveStorageProvider(RestClient client,CredentialsSource credentials,String rootFolderId,String sharedDriveId){
        this.client=client;this.credentials=credentials;this.rootFolderId=rootFolderId;
        this.sharedDriveId=sharedDriveId;
    }
    @Override public StorageFolderTree ensureProductTree(StorageEnsureRequest request){
        String configuredRoot=identifier(rootFolderId,"GOOGLE_DRIVE_ROOT_FOLDER_ID");
        String configuredShared=sharedDriveId==null||sharedDriveId.isBlank()?null:identifier(sharedDriveId,"GOOGLE_SHARED_DRIVE_ID");
        String token=token();
        String productFolder=ensure(token,configuredRoot,request.productId(),request.productUuid(),"PRODUCT",configuredShared);
        Map<StorageFolderRole,String> children=new EnumMap<>(StorageFolderRole.class);
        for(StorageFolderRole role:StorageFolderRole.values())children.put(role,ensure(token,productFolder,role.folderName(),request.productUuid(),role.name(),configuredShared));
        return new StorageFolderTree(configuredRoot,configuredShared,productFolder,children);
    }
    private String ensure(String token,String parent,String name,UUID productUuid,String role,String shared){
        String found=find(token,parent,productUuid,role,shared); return found==null?create(token,parent,name,productUuid,role,shared):found;
    }
    private String find(String token,String parent,UUID productUuid,String role,String shared){
        String q="'"+parent+"' in parents and trashed=false and appProperties has { key='product_uuid' and value='"+productUuid+"' } and appProperties has { key='folder_role' and value='"+role+"' }";
        DriveList response=call(()->client.get().uri(builder->{var b=builder.path("/drive/v3/files").queryParam("q","{driveQuery}")
                .queryParam("fields","files(id)").queryParam("pageSize",2).queryParam("spaces","drive");
            if(shared!=null)b.queryParam("supportsAllDrives",true).queryParam("includeItemsFromAllDrives",true).queryParam("corpora","drive").queryParam("driveId",shared);return b.build(Map.of("driveQuery",q));})
                .headers(h->h.setBearerAuth(token)).retrieve().body(DriveList.class));
        if(response==null||response.files()==null||response.files().isEmpty())return null;
        if(response.files().size()!=1)throw new StorageProviderException("STORAGE_FOLDER_STATE_CONFLICT","Google Drive contains duplicate managed folders");
        return validResponseId(response.files().getFirst().id());
    }
    private String create(String token,String parent,String name,UUID productUuid,String role,String shared){
        Map<String,Object> body=new LinkedHashMap<>();body.put("name",name);body.put("mimeType","application/vnd.google-apps.folder");
        body.put("parents",List.of(parent));body.put("appProperties",Map.of("product_uuid",productUuid.toString(),"folder_role",role));
        DriveFile response=call(()->client.post().uri(builder->{var b=builder.path("/drive/v3/files").queryParam("fields","id");
            if(shared!=null)b.queryParam("supportsAllDrives",true);return b.build();}).headers(h->h.setBearerAuth(token)).body(body).retrieve().body(DriveFile.class));
        return validResponseId(response==null?null:response.id());
    }
    private <T>T call(java.util.concurrent.Callable<T> request){for(int attempt=1;attempt<=ATTEMPTS;attempt++)try{return request.call();}
        catch(RestClientResponseException e){if(attempt<ATTEMPTS&&(e.getStatusCode().value()==429||e.getStatusCode().is5xxServerError()))continue;throw failure(e.getStatusCode(),e);}
        catch(RestClientException e){if(attempt<ATTEMPTS)continue;throw new StorageProviderException("GOOGLE_PROVIDER_UNAVAILABLE","Google Drive is temporarily unavailable",e);}
        catch(StorageProviderException e){throw e;}catch(Exception e){throw new StorageProviderException("GOOGLE_PROVIDER_UNAVAILABLE","Google Drive is temporarily unavailable",e);}
        throw new IllegalStateException("retry exhausted");}
    private String token(){try{GoogleCredentials c=credentials.load().createScoped(List.of(SCOPE));c.refreshIfExpired();if(c.getAccessToken()==null)c.refresh();
        if(c.getAccessToken()==null||c.getAccessToken().getTokenValue()==null)throw new IOException("missing token");return c.getAccessToken().getTokenValue();}
        catch(IOException e){throw new StorageProviderException("GOOGLE_AUTH_UNAVAILABLE","Google authentication is unavailable",e);}}
    private StorageProviderException failure(HttpStatusCode status,Exception cause){if(status.value()==401)return new StorageProviderException("GOOGLE_AUTH_UNAVAILABLE","Google authentication failed",cause);
        if(status.value()==403)return new StorageProviderException("GOOGLE_PERMISSION_DENIED","Google Drive access was denied",cause);
        if(status.value()==429)return new StorageProviderException("GOOGLE_RATE_LIMITED","Google Drive rate limit was reached",cause);
        return new StorageProviderException("GOOGLE_PROVIDER_UNAVAILABLE","Google Drive is temporarily unavailable",cause);}
    private static String identifier(String value,String field){if(value==null||!value.matches("[A-Za-z0-9_-]{3,256}"))throw new StorageProviderException("CONNECTOR_NOT_CONFIGURED",field+" is not configured");return value;}
    private static String validResponseId(String value){if(value==null||!value.matches("[A-Za-z0-9_-]{3,256}"))throw new StorageProviderException("GOOGLE_PROVIDER_UNAVAILABLE","Google Drive returned an invalid folder identifier");return value;}
    private static GoogleCredentials adc()throws IOException{return GoogleCredentials.getApplicationDefault();}
    private static RestClient fixedClient(){HttpClient hc=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();JdkClientHttpRequestFactory rf=new JdkClientHttpRequestFactory(hc);rf.setReadTimeout(Duration.ofSeconds(15));return RestClient.builder().baseUrl("https://www.googleapis.com").requestFactory(rf).build();}
    private record DriveFile(String id){} private record DriveList(List<DriveFile> files){}
    @FunctionalInterface interface CredentialsSource{GoogleCredentials load()throws IOException;}
}
