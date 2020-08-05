package com.marklogic.utils;

import com.marklogic.client.io.DocumentMetadataHandle.Capability;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentPermissions;

public class GenUtils {
	
	public void parsePermissions(String[] tokens, DocumentPermissions permissions) {
            for (int i = 0; i < tokens.length; i += 2) {
                String role = tokens[i];
                if (i + 1 >= tokens.length) {
                	throw new IllegalArgumentException("Unable to parse permissions string, which must be a comma-separated " +
		                "list of role names and capabilities - i.e. role1,read,role2,update,role3,execute; string: ");
                }
                String capability = tokens[i + 1];
                Capability c = null;
                if (capability.equals("execute")) {
                    c = Capability.EXECUTE;
                } else if (capability.equals("insert")) {
                    c = Capability.INSERT;
                } else if (capability.equals("update")) {
                    c = Capability.UPDATE;
                } else if (capability.equals("read")) {
                    c = Capability.READ;
                }
                if (permissions.containsKey(role)) {
                	permissions.get(role).add(c);
                } else {
	                permissions.add(role, c);
                }
            }
        
    }

}
