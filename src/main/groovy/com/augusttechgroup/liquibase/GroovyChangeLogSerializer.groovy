//
// Groovy Liquibase ChangeLog
//
// Copyright (C) 2010 Tim Berglund
// http://augusttechgroup.com
// Littleton, CO
//
// Licensed under the Apache License 2.0
//

package com.augusttechgroup.liquibase

import liquibase.serializer.ChangeLogSerializer
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.ChangeSet
import liquibase.change.Change
import liquibase.sql.visitor.SqlVisitor
import liquibase.change.ColumnConfig
import liquibase.util.ISODateFormat
import liquibase.change.ConstraintsConfig
import liquibase.change.ChangeProperty
import liquibase.change.TextNode
import java.sql.Timestamp


/**
 *
 *
 * @author Tim Berglund
 */
class GroovyChangeLogSerializer
  implements ChangeLogSerializer
{
  ISODateFormat isoFormat = new ISODateFormat()


  String[] getValidFileExtensions() {
    ['groovy']
  }


  String serialize(DatabaseChangeLog databaseChangeLog) {
    //TODO This is not implemented in the Liquibase XML serializer either
    return null
  }


  String serialize(ChangeSet changeSet) {
    def attrNames = [ 'id', 'author', 'runAlways', 'runOnChange', 'failOnError', 'context', 'dbms' ]
    def attributes = [
      id: changeSet.id,
      author: changeSet.author
    ]
    def children = []

    if(changeSet.isAlwaysRun()) {
      attributes.runAlways = true
    }

    if(changeSet.isRunOnChange()) {
      attributes.runOnChange = true
    }

    if(changeSet.failOnError) {
      attributes.failOnError = changeSet.failOnError?.toString()
    }

    if(changeSet.contexts) {
      attributes.context = changeSet.getContexts().join(',')
    }

    if(changeSet.dbmsSet) {
      attributes.dbms = changeSet.dbmsSet.join(',')
    }

    if(changeSet.comments?.trim()) {
      children << "comment \"${changeSet.comments}\""
    }

    changeSet.changes.each { change -> children << "  ${serialize(change)}" }

    return """\
changeSet(${buildPropertyListFrom(attrNames, attributes).join(', ')}) {
${children.join('\n')}
}"""
  }


  String serialize(SqlVisitor visitor) {
    return null
  }


  String serialize(Change change) {
    def fields = getChangeFieldsToSerialize(change)
    def children = []
    def attributes = []
    def textBody
    fields.each { field ->
      def fieldName = field.name
      def fieldValue = change[fieldName]

      def textNodeAnnotation = field.getAnnotation(TextNode.class)
      if(textNodeAnnotation) {
        textBody = fieldValue
      }
      else if(fieldValue instanceof Collection) {
        fieldValue.findAll { it instanceof ColumnConfig }.each {
          children << serialize(it)
        }
      }
      else if(fieldValue instanceof ColumnConfig) {
        children << serialize(fieldValue)
      }
      else if(fieldName in [ 'procedureBody', 'sql', 'selectQuery' ]) {
        textBody = fieldValue
      }
      else  {
        attributes << fieldName
      }
    }

    attributes = attributes.sort { it } 

    def serializedChange
    if(attributes) {
      serializedChange = "${change.changeMetaData.name}(${buildPropertyListFrom(attributes, change).join(', ')})"
    }
    else {
      serializedChange = "${change.changeMetaData.name}"
    }

    if(children) {
      serializedChange = """\
${serializedChange} {
  ${children.join("\n  ")}
}"""
    }
    else if(textBody) {
      serializedChange = """\
${serializedChange} {
  "${textBody}"
}"""
    }

    return serializedChange
  }


  String serialize(ColumnConfig columnConfig) {
    def propertyNames = [ 'name', 'type', 'value', 'valueNumeric', 'valueDate', 'valueBoolean', 'valueComputed', 'defaultValue', 'defaultValueNumeric', 'defaultValueDate', 'defaultValueBoolean', 'defaultValueComputed', 'autoIncrement', 'remarks' ]
    def properties = buildPropertyListFrom(propertyNames, columnConfig)
    def column = "column(${properties.join(', ')})"
    if(columnConfig.constraints) {
      """\
${column} {
  ${serialize(columnConfig.constraints)}
}"""
    }
    else {
      column
    }
  }


  String serialize(ConstraintsConfig constraintsConfig) {
    def propertyNames = [ 'nullable', 'primaryKey', 'primaryKeyName', 'primaryKeyTablespace', 'references', 'unique', 'uniqueConstraintName', 'check', 'deleteCascade', 'foreignKeyName', 'initiallyDeferred', 'deferrable' ]
    "constraints(${buildPropertyListFrom(propertyNames, constraintsConfig).join(', ')})"
  }


  private buildPropertyListFrom(propertyNames, object) {
    def properties = []

    propertyNames.each { propertyName ->
      def propertyString
      def propertyValue = object[propertyName]
      if(propertyValue != null) {
        switch(propertyValue.class) {
          case Boolean:
            propertyString = Boolean.toString(propertyValue)
            break

          case BigInteger:
          case BigDecimal:
          case Number:
            propertyString = propertyValue.toString()
            break

          case Timestamp:
            propertyString = "'${isoFormat.format((Timestamp)propertyValue)}'"
            break

          default:
            propertyString = "'${propertyValue.toString()}'"
            break
        }
        properties << "${propertyName}: ${propertyString}"
      }
    }

    return properties
  }


  private getChangeFieldsToSerialize(Change change) {
    def fields = change.class.declaredFields

    // Find all fields that don't have these two excluded names
    fields = fields.findAll { field -> !(field.name in [ 'serialVersionUID', '$VRc' ]) }

    // Find all fields that don't have the @ChangeProperty(includeInSerialization=false) annotation
    fields = fields.findAll { field ->
      def annotation = field.getAnnotation(ChangeProperty)
      !(annotation && !annotation?.includeInSerialization())
    }

    return fields
  }
  
}
