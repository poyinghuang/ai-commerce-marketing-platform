package com.aicommerce.platform.connector.drive.application;

public class StorageProviderException extends RuntimeException {
    private final String code;
    public StorageProviderException(String code,String message){super(message);this.code=code;}
    public StorageProviderException(String code,String message,Throwable cause){super(message,cause);this.code=code;}
    public String getCode(){return code;}
}
