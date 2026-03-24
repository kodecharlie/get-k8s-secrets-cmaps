grammar HelmK8sKeyValue;

@header {
package com.minerva.helmkv;
}

file
    : element* EOF
    ;

element
    : DOC_SEP
    | KIND_LINE
    | SECTION_LINE
    | KV_LINE
    | BLANK_LINE
    | OTHER_LINE
    ;

DOC_SEP
    : WS* '---' WS* NL
    ;

KIND_LINE
    : WS* 'kind' WS* ':' WS* ('ConfigMap' | 'Secret') WS* NL
    ;

SECTION_LINE
    : WS* ('data' | 'stringData') WS* ':' WS* NL
    ;

// Captures lines like:
//   key: value
//   "1": {{ .Values.foo }}
//   .dockerconfigjson: {{ .Values.bar }}
KV_LINE
    : INDENT KV_KEY WS* ':' WS* KV_VALUE? NL
    ;

BLANK_LINE
    : WS* NL
    ;

OTHER_LINE
    : ~[\r\n]* NL
    ;

fragment INDENT
    : '  '+
    ;

fragment KV_KEY
    : DQ_STRING
    | SQ_STRING
    | [A-Za-z0-9_.\\-]+
    ;

fragment KV_VALUE
    : ~[\r\n]+
    ;

fragment DQ_STRING
    : '"' (~["\r\n] | '\\"')* '"'
    ;

fragment SQ_STRING
    : '\'' (~['\r\n] | '\\\'')* '\''
    ;

fragment WS
    : [ \t]
    ;

fragment NL
    : '\r'? '\n'
    ;
