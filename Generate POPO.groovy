import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil

typeMapping = [
        (~/(?i)^bit$/)                                    : "BIT",
        (~/(?i)^tinyint$/)                                : "SMALLINT",
        (~/(?i)^uniqueidentifier|uuid$/)                  : "UUID",
        (~/(?i)^int|integer|number$/)                     : "INTEGER",
        (~/(?i)^bigint$/)                                 : "BIGINT",
        (~/(?i)^varbinary|image$/)                        : "BYTEA",
        (~/(?i)^double|float|real$/)                      : "DOUBLE_PRECISION",
        (~/(?i)^decimal|money|numeric|smallmoney$/)       : "DECIMAL",
        (~/(?i)^datetime|datetime2|timestamp|date|time$/) : "DATETIME",
        (~/(?i)^daterange$/)                              : "DATERANGE",
        (~/(?i)^char$/)                                   : "CHAR",
]

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable && it.getKind() == ObjectKind.TABLE }.each { generate(it, dir) }
}


def generate(table, dir) {
    def className = pythonClassName(table.getName())
    def fields = calcFields(table)
    new File(dir, className + ".py").withPrintWriter { out -> generate(out, className, fields) }
}

def generate(out, className, fields) {
    out.println "from sqlalchemy import Column, String, ForeignKey, Table"
    out.println "from sqlalchemy.dialects.postgresql import BIT, SMALLINT, UUID, INTEGER, BIGINT, BYTEA, DOUBLE_PRECISION, DECIMAL, DATETIME, DATERANGE, CHAR"
    out.println "class ${className}(base):"
    out.println "    __tablename__ = '${toSnakeCase(className)}'"
    out.println "    __table_args__ = \u007B'schema':'type_837'\u007D"
    fields.each() {
        out.print "    ${toSnakeCase(it.name)} = Column(${it.type}"
        if (it.primary) out.print(", primary_key=True")
        if (it.foreign) {
            it.foreignKeys.each() { fk -> 
                if (DasUtil.containsName(it.colName, fk.getColumnsRef())) {
                    out.print(", ForeignKey(${pythonClassName(fk.getRefTableName())}.${toSnakeCase(fk.getColumnsRef().iterate().next())})")
                    // str = ""
                    // fk.getColumnsRef().iterate().metaClass.methods.name.unique().each{ 
                    //     str += it+"(); "
                    // }
                    //out.println(str)
                }
            }            
        }
        out.println(")")
    }
    out.println ""
    out.println ""
}

def String toSnakeCase( String text ) {
    return text.replaceAll( /([A-Z])/, /_$1/ ).toLowerCase().replaceAll( /^_/, '' )
}

def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }?.value ?: "String"
        def foreign = DasUtil.isForeign(col)
        def foreignKeys = DasUtil.getForeignKeys(table)
        fields += [[
                           name : pythonClassName(col.getName()),
                           colName: col.getName(),
                           type : typeStr,
                           primary: DasUtil.isPrimary(col),
                           foreign: foreign,
                           foreignKeys: foreignKeys
                    ]]
    }
}

def pythonClassName(str) {
    com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
}