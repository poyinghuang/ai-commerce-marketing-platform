package com.aicommerce.platform.connector.drive.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.aicommerce.platform.connector.drive.application.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductStorageFolderController.class)
class ProductStorageFolderControllerTest {
    @Autowired MockMvc mvc;@MockitoBean ProductStorageFolderService commands;@MockitoBean ProductStorageFolderQueryService queries;
    @Test void createReturnsLocationAndEtag()throws Exception{UUID product=UUID.randomUUID();var view=view(product);
        when(commands.ensure(eq(product),anyString())).thenReturn(new EnsureStorageFolderResult(view,true));
        mvc.perform(post("/api/products/{id}/storage-folder",product).header("X-Request-ID","drive-web"))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/products/"+product+"/storage-folder"))
                .andExpect(header().string("ETag","W/\"0\"")).andExpect(jsonPath("$.subfolders.IMAGES").value("images"));}
    @Test void repeatedEnsureReturnsOk()throws Exception{UUID product=UUID.randomUUID();when(commands.ensure(eq(product),anyString())).thenReturn(new EnsureStorageFolderResult(view(product),false));
        mvc.perform(post("/api/products/{id}/storage-folder",product)).andExpect(status().isOk());}
    @Test void getMissingUsesStandardError()throws Exception{UUID product=UUID.randomUUID();when(queries.get(product)).thenThrow(new StorageFolderNotFoundException());
        mvc.perform(get("/api/products/{id}/storage-folder",product)).andExpect(status().isNotFound()).andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.code").value("STORAGE_FOLDER_NOT_FOUND"));}
    private ProductStorageFolderView view(UUID product){return new ProductStorageFolderView(UUID.randomUUID(),product,"GOOGLE_DRIVE","root",null,"product",Map.of("IMAGES","images"),Instant.now(),Instant.now(),0);}
}
