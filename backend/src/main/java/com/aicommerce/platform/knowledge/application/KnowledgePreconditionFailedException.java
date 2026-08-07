package com.aicommerce.platform.knowledge.application;
public class KnowledgePreconditionFailedException extends RuntimeException { public KnowledgePreconditionFailedException() { super("Knowledge version does not match If-Match"); } }
