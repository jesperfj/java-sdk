/**
 * Copyright (c) 2011, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.force.sdk.codegen.renderer;

import static com.force.sdk.codegen.ForceJPAClassGeneratorUtils.renderJavaName;

import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Generated;
import javax.persistence.*;

import org.antlr.stringtemplate.AttributeRenderer;
import org.codehaus.jackson.annotate.JsonSetter;

import com.force.sdk.codegen.ForceJPAClassGenerator;
import com.force.sdk.codegen.builder.*;
import com.sforce.soap.partner.*;

/**
 * A StringTemplate {@code AttributeRenderer} that renders Java field level {@code String}s.
 * <p>
 * This {@code AttributeRenderer} effectively translates between a Force.com API {@code Field}
 * object and Java field level code.  Within StringTemplate, it is meant to be registered
 * as an {@code AttributeRenderer} for a Force.com API {@code Field} object.
 *
 * @author Tim Kral
 */
public class ForceJacksonJSONFieldRenderer implements AttributeRenderer {

    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("^(enum|getter|setter)Annotation(\\d+)$");
    private static final Pattern ENUM_VALUES_PATTERN = Pattern.compile("^enumValues(\\d+)$");
    private static final Pattern FIELD_COMMENTS_PATTERN = Pattern.compile("^fieldComments(\\d+)$");
    
    @Override
    public String toString(Object o) {
        return "";
    }

    @Override
    public String toString(Object o, String format) {
        Field field = (Field) o;
        Matcher matcher;
        
        if ("fieldName".equals(format)) {
            return renderJavaName(field, true /*firstCharLowerCase*/);
        } else if ("fieldType".equals(format)) {
            return renderFieldType(field);
        } else if ((matcher = FIELD_COMMENTS_PATTERN.matcher(format)).matches()) {
            // The number at the end of an fieldComments format tells
            // us the number of indents to add to the fieldComments
            String numIndentsStr = matcher.group(1);
            JavaCommentBuilder builder = new JavaCommentBuilder(Integer.valueOf(numIndentsStr));
            
            return renderFieldComments(field, builder);
        } else if (format != null && format.contains("fieldComments")) {
            throw new IllegalArgumentException("Unrecognized format: " + format + ". "
                + "fieldComments formats must end in the number of indents required (e.g. fieldComments1");
        } else if ((matcher = ANNOTATION_PATTERN.matcher(format)).matches()) {
            String annotationType = matcher.group(1);
            
            // The number at the end of an annotation format tells
            // us the number of indents to add to the annotations
            String numIndentsStr = matcher.group(2);
            JacksonJSONAnnotationBuilder builder = new JacksonJSONAnnotationBuilder(Integer.valueOf(numIndentsStr));
            
            if ("enum".equals(annotationType)) {
                return renderEnumAnnotation(field, builder);
            } else if ("getter".equals(annotationType)) {
                return renderGetterAnnotation(field, builder);
            } else if ("setter".equals(annotationType)) {
                return renderSetterAnnotation(field, builder);
            }
        } else if (format != null && format.contains("Annotation")) {
            throw new IllegalArgumentException("Unrecognized annotation format: " + format + ". "
                    + "Annotation formats must be one of (enum, getter, setter) "
                    + "and must end in the number of indents required (e.g. getterAnnotation1");
        } else if ("methodName".equals(format)) {
            return renderJavaName(field, false /*firstCharLowerCase*/);
        } else if ("enumName".equals(format)) {
            return renderEnumName(field);
        } else if ((matcher = ENUM_VALUES_PATTERN.matcher(format)).matches()) {
            // The number at the end of an enumValues format tells
            // us the number of indents to add to the enumValues
            String numIndentsStr = matcher.group(1);
            ForcePicklistEnumBuilder builder = new ForcePicklistEnumBuilder(Integer.valueOf(numIndentsStr));
            
            return renderEnumValues(field, builder);
        } else if (format != null && format.contains("enumValues")) {
            throw new IllegalArgumentException("Unrecognized format: " + format + ". "
                + "enumValues formats must end in the number of indents required (e.g. enumValues1");
        }
        
        return toString(o);
    }
    
    // Render the Java field type for a given API field
    private String renderFieldType(Field field) {
        FieldType type = field.getType();
        if (type == FieldType._boolean
            || type == FieldType.combobox) {
            return boolean.class.getName();
        } else if (type == FieldType._double
                   || type == FieldType.percent) {
            return double.class.getName();
        } else if (type == FieldType._int) {
            return int.class.getName();
        } else if (type == FieldType.currency) {
            return BigDecimal.class.getName();
        } else if (type == FieldType.date
                   || type == FieldType.time) {
            return Date.class.getName();
        } else if (type == FieldType.datetime) {
            return Calendar.class.getName();
// Jackson can't deserialize URLs that are malformed, so just use String as type.
//        } else if (type == FieldType.url) {
//            return String.class.getName();
        } else if (type == FieldType.picklist) {
            // Force the field type to be an enum for restricted picklists
            // with API enabled picklist values
            if (field.isRestrictedPicklist() && field.getPicklistValues().length > 0) return renderEnumName(field);
            
            return String.class.getSimpleName();
        } else if (type == FieldType.multipicklist) {
            // Force the field type to be an enum array for restricted multipicklists
            // with API enabled picklist values
            if (field.isRestrictedPicklist() && field.getPicklistValues().length > 0) return renderEnumName(field) + "[]";
            
            return String[].class.getSimpleName();
// Punting on references for now. We definitely want an xxxId String field so we have the
// id of the referenced object. JPA just has an object. Probably better to do both.
//        } else if (type == FieldType.reference) {
//            String[] referenceTo;
//            // Return the class name of the relationship if there's only one relationship object
//            // Otherwise, we'll just use a String
//            if ((referenceTo = field.getReferenceTo()).length == 1) {
//                DescribeSObjectResult dsr = new DescribeSObjectResult();
//                dsr.setName(referenceTo[0]);
//            
//            return renderJavaName(dsr, false /*firstCharLowerCase*/);
//            }
        }
        
        return String.class.getSimpleName();
    }

    private String renderFieldComments(Field field, JavaCommentBuilder builder) {
        if (field.getType() == FieldType.reference
            && field.getReferenceTo().length > 1) {
            
            builder.add(renderJavaName(field, true) + " possible references:");
            for (String refTo : field.getReferenceTo()) {
                builder.add(refTo);
            }
        }
        
        return builder.toString();
    }
    
    private String renderEnumAnnotation(Field field, JacksonJSONAnnotationBuilder builder) {
        return builder.toString();
    }
    
    // Render annotations that are to be add to a JPA object
    // at the method level
    private String renderGetterAnnotation(Field field, JacksonJSONAnnotationBuilder builder) {
        return builder.toString();
    }
    
    private String renderSetterAnnotation(Field field, JacksonJSONAnnotationBuilder builder) {
        
        // Mark non nullable, non defaulted fields as required
    	builder.add(JsonSetter.class,Collections.<String,String>singletonMap("value",field.getName()));
//        if (!field.isNillable() && !field.isDefaultedOnCreate()) {
//            builder.add(Basic.class,
//                    Collections.<String, String>singletonMap("optional", "false"));
//        }

        return builder.toString();
    }
    
    private String renderEnumName(Field field) {
        StringBuffer enumName = new StringBuffer(renderJavaName(field, false));
        enumName.append("Enum");
        
        return enumName.toString();
    }
    
    private String renderEnumValues(Field field, ForcePicklistEnumBuilder builder) {
        for (PicklistEntry picklistEntry : field.getPicklistValues()) {
            builder.add(picklistEntry);
        }
        
        return builder.toString();
    }
}
