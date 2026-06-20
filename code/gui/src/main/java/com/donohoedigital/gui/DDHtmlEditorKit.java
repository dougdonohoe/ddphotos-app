/*
 * DDHtmlEditorKit.java
 *
 * Created on March 29, 2003, 3:32 PM
 */
package com.donohoedigital.gui;

import com.donohoedigital.config.*;

import javax.swing.text.*;
import javax.swing.text.html.*;
import java.util.*;

public class DDHtmlEditorKit extends HTMLEditorKit {
    private StyleSheet sheet_;

    private static final HashMap<String, Class<?>> hmTagViewClasses_ = new HashMap<>();

    static {
        registerTagViewClass("img", DDImageView.class);
        registerTagViewClass("ddimg", DDImageView.class);
    }

    public DDHtmlEditorKit(StyleSheet proto) {
        sheet_ = proto;
    }

    public static void registerTagViewClass(String tagName, Class<?> viewClass) {
        hmTagViewClasses_.put(tagName, viewClass);
    }

    public ViewFactory getViewFactory() {
        return new HTMLFactoryX();
    }

    public static class HTMLFactoryX extends HTMLFactory {
        Class<?>[] ctorArgs_ = new Class[]{Element.class};

        public View create(Element elem) {
            Object o = elem.getAttributes().getAttribute(StyleConstants.NameAttribute);

            Class<?> clazz = hmTagViewClasses_.get(o.toString());

            if (clazz != null) {
                return ConfigUtils.newInstance(clazz, ctorArgs_, new Object[]{elem});
            }
            return super.create(elem);
        }
    }

    /**
     * Override to return our own style sheet, instead of global sheet
     */
    public StyleSheet getStyleSheet() {
        if (sheet_ == null) {
            // copy parent's style sheet so we can add our own values
            sheet_ = new StyleSheet();
            sheet_.addStyleSheet(super.getStyleSheet());
        }
        return sheet_;
    }
}
