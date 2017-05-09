/*
    Copyright (C) 2006-2007 Serotonin Software Technologies Inc.
 	@author Matthew Lohbihler
 */
package com.serotonin.web.i18n;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author Matthew Lohbihler
 */
public class LocalizableMessage {
    public static String getMessage(ResourceBundle bundle, String key) {
        return new LocalizableMessage(key).getLocalizedMessage(bundle);
    }

    public static String getMessage(ResourceBundle bundle, String key, Object... args) {
        return new LocalizableMessage(key, args).getLocalizedMessage(bundle);
    }

    private final String key;
    private final Object[] args;

    public LocalizableMessage(String key) {
        this(key, (Object[]) null);
    }

    public LocalizableMessage(String key, Object... args) {
        if (key == null)
            throw new NullPointerException("key cannot be null");

        this.key = key;

        if (args != null) {
            this.args = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null)
                    this.args[i] = "";
                else if (args[i] instanceof LocalizableMessage)
                    this.args[i] = args[i];
                else
                    this.args[i] = args[i].toString();
            }
        }
        else
            this.args = new Object[0];
    }

    public String getLocalizedMessage(ResourceBundle bundle) {
        if (bundle == null)
            return "?x?" + key + "?x?";
        return getLocalizedMessageImpl(bundle, this);
    }

    private static String getLocalizedMessageImpl(ResourceBundle bundle, LocalizableMessage lm) {
        // Resolve any args that are themselves localizable messages to strings.
        Object[] resolvedArgs = new Object[lm.args.length];
        for (int i = 0; i < resolvedArgs.length; i++) {
            if (lm.args[i] instanceof LocalizableMessage)
                resolvedArgs[i] = getLocalizedMessageImpl(bundle, (LocalizableMessage) lm.args[i]);
            else
                resolvedArgs[i] = lm.args[i];
        }

        try {
            String pattern = bundle.getString(lm.key);
            return MessageFormat.format(pattern, resolvedArgs);
        }
        catch (MissingResourceException e) {
            return "???" + lm.key + "(30:" + (bundle.getLocale() == null ? "null" : bundle.getLocale().getLanguage())
                    + ")???";
        }
    }

    public String getKey() {
        return key;
    }

    public Object[] getArgs() {
        return args;
    }

    public String serialize() {
        return serializeImpl(this, false);
    }

    private static String serializeImpl(LocalizableMessage lm, boolean nested) {
        StringBuilder sb = new StringBuilder();
        if (nested)
            sb.append('[');
        sb.append(encodeString(lm.key));
        if (lm.args != null) {
            for (Object o : lm.args) {
                if (o instanceof LocalizableMessage)
                    sb.append(serializeImpl((LocalizableMessage) o, true));
                else
                    sb.append(encodeString((String) o));
            }
        }
        if (nested)
            sb.append(']');
        return sb.toString();
    }

    public static LocalizableMessage deserialize(String s) throws LocalizableMessageParseException {
        return deserializeImpl(new StringBuilder(s));
    }

    private static LocalizableMessage deserializeImpl(StringBuilder sb) throws LocalizableMessageParseException {
        int pos = 0;
        String key = null;
        List<Object> args = new ArrayList<Object>();
        while (true) {
            if (sb.length() == 0)
                throw new LocalizableMessageParseException("Invalid localizable message encoding");

            if (sb.charAt(0) == '[') {
                // nested message
                sb.deleteCharAt(0);
                args.add(deserializeImpl(sb));

                if (sb.length() == 0)
                    break;
            }
            else if (sb.charAt(0) == ']') {
                // end of nested message
                sb.deleteCharAt(0);
                break;
            }
            else {
                pos = sb.indexOf("|", pos);
                if (pos == -1)
                    throw new LocalizableMessageParseException("Invalid localizable message encoding");
                else if (pos == 0 || sb.charAt(pos - 1) != '\\') {
                    String str = decodeString(sb.substring(0, pos + 1));

                    if (key == null)
                        key = str;
                    else
                        args.add(str);

                    sb.delete(0, pos + 1);
                    if (sb.length() == 0)
                        break;

                    pos = 0;
                }
                else
                    pos++;
            }
        }

        Object[] a = new Object[args.size()];
        args.toArray(a);
        return new LocalizableMessage(key, a);
    }

    private static String encodeString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 10);

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '|':
                sb.append("\\|");
                break;
            case '[':
                sb.append("\\[");
                break;
            case ']':
                sb.append("\\]");
                break;
            default:
                sb.append(c);
            }
        }

        sb.append('|');

        return sb.toString();
    }

    private static String decodeString(String s) {
        StringBuilder sb = new StringBuilder(s.length());

        int i = 0;
        int l = s.length() - 1;
        while (i < l) {
            char c1 = s.charAt(i);
            if (c1 == '\\') {
                char c2 = s.charAt(i + 1);
                switch (c2) {
                case '|':
                case '[':
                case ']':
                    c1 = c2;
                    i++;
                    break;
                }
            }

            sb.append(c1);
            i++;
        }

        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        LocalizableMessage lm1 = new LocalizableMessage("nest1key", "nest1param1", "nest1param2");
        LocalizableMessage lm2 = new LocalizableMessage("nest2key", "nest2param1", "nest2param2");
        LocalizableMessage lm3 = new LocalizableMessage("outerkey", lm1, lm2);

        String enc = lm3.serialize();
        System.out.println(enc);
        LocalizableMessage dec = LocalizableMessage.deserialize(enc);
        System.out.println(dec.getKey());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(args);
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final LocalizableMessage other = (LocalizableMessage) obj;
        if (!Arrays.equals(args, other.args))
            return false;
        if (key == null) {
            if (other.key != null)
                return false;
        }
        else if (!key.equals(other.key))
            return false;
        return true;
    }
}