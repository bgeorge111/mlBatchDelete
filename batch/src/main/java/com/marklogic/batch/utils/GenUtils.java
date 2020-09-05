package com.marklogic.batch.utils;

import com.marklogic.client.io.DocumentMetadataHandle.Capability;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentPermissions;

public class GenUtils {

	public void parsePermissions(String[] tokens, DocumentPermissions permissions) {
		for (int i = 0; i < tokens.length; i += 2) {
			String role = tokens[i];
			if (i + 1 >= tokens.length) {
				throw new IllegalArgumentException(
						"Unable to parse permissions string, which must be a comma-separated "
								+ "list of role names and capabilities - i.e. role1,read,role2,update,role3,execute; string: ");
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

	/**
	 * Checks if a CharSequence is null or empty ("")
	 *
	 * @param value
	 * @return true if the value is null or empty
	 */
	public static boolean isEmpty(final CharSequence value) {
		return value == null || value.length() == 0;
	}

	/**
	 * Checks if a CharSequence is not null or empty ("")
	 *
	 * @param value
	 * @return
	 */
	public static boolean isNotEmpty(final CharSequence value) {
		return !isEmpty(value);
	}

	/**
	 * Checks if a CharSequence is null or whitespace-only characters
	 *
	 * @param value
	 * @return {@code true} if the value is null, empty, or whitespace-only
	 *         characters; {@code false} otherwise.
	 */
	public static boolean isBlank(final CharSequence value) {
		int length;
		if (value == null || (length = value.length()) == 0) {
			return true;
		}
		for (int i = 0; i < length; i++) {
			if (!Character.isWhitespace(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if a CharSequence is not null and not whitespace-only characters
	 *
	 * @param value
	 * @return
	 */
	public static boolean isNotBlank(final CharSequence value) {
		return !isBlank(value);
	}

}
