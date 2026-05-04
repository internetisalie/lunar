// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsElementType;
import net.internetisalie.lunar.luacats.lang.psi.impl.*;

public interface LuaCatsElementTypes {

  IElementType ADD_OR_SUBTRACT = new LuaCatsElementType("ADD_OR_SUBTRACT");
  IElementType ALIAS_TAG = new LuaCatsElementType("ALIAS_TAG");
  IElementType ARG_KEYWORD = new LuaCatsElementType("ARG_KEYWORD");
  IElementType ARG_NAME = new LuaCatsElementType("ARG_NAME");
  IElementType ARG_SYMBOL = new LuaCatsElementType("ARG_SYMBOL");
  IElementType ARG_TYPE = new LuaCatsElementType("ARG_TYPE");
  IElementType ARG_VALUE = new LuaCatsElementType("ARG_VALUE");
  IElementType ARRAY_TYPE = new LuaCatsElementType("ARRAY_TYPE");
  IElementType ASYNC_TAG = new LuaCatsElementType("ASYNC_TAG");
  IElementType BUILTIN_TYPE = new LuaCatsElementType("BUILTIN_TYPE");
  IElementType CAST_MODIFIER = new LuaCatsElementType("CAST_MODIFIER");
  IElementType CAST_TAG = new LuaCatsElementType("CAST_TAG");
  IElementType CLASS_TAG = new LuaCatsElementType("CLASS_TAG");
  IElementType COMMENT = new LuaCatsElementType("COMMENT");
  IElementType DEPRECATED_TAG = new LuaCatsElementType("DEPRECATED_TAG");
  IElementType DESCRIPTION = new LuaCatsElementType("DESCRIPTION");
  IElementType DIAGNOSTICS = new LuaCatsElementType("DIAGNOSTICS");
  IElementType DIAGNOSTIC_STATE = new LuaCatsElementType("DIAGNOSTIC_STATE");
  IElementType DIAGNOSTIC_TAG = new LuaCatsElementType("DIAGNOSTIC_TAG");
  IElementType DICTIONARY_TYPE = new LuaCatsElementType("DICTIONARY_TYPE");
  IElementType DISTINCT_TYPE = new LuaCatsElementType("DISTINCT_TYPE");
  IElementType ENUM_TAG = new LuaCatsElementType("ENUM_TAG");
  IElementType FIELD_DESCRIPTOR = new LuaCatsElementType("FIELD_DESCRIPTOR");
  IElementType FIELD_KEY_DESCRIPTOR = new LuaCatsElementType("FIELD_KEY_DESCRIPTOR");
  IElementType FIELD_NAME_DESCRIPTOR = new LuaCatsElementType("FIELD_NAME_DESCRIPTOR");
  IElementType FIELD_SCOPE = new LuaCatsElementType("FIELD_SCOPE");
  IElementType FIELD_TAG = new LuaCatsElementType("FIELD_TAG");
  IElementType FUNCTION_SIGNATURE_ARGUMENT = new LuaCatsElementType("FUNCTION_SIGNATURE_ARGUMENT");
  IElementType FUNCTION_SIGNATURE_RETURN_TYPE = new LuaCatsElementType("FUNCTION_SIGNATURE_RETURN_TYPE");
  IElementType FUNCTION_SIGNATURE_TYPE = new LuaCatsElementType("FUNCTION_SIGNATURE_TYPE");
  IElementType GENERIC_TAG = new LuaCatsElementType("GENERIC_TAG");
  IElementType GENERIC_TYPE = new LuaCatsElementType("GENERIC_TYPE");
  IElementType GENERIC_TYPE_PARAM = new LuaCatsElementType("GENERIC_TYPE_PARAM");
  IElementType GENERIC_TYPE_PARAMS = new LuaCatsElementType("GENERIC_TYPE_PARAMS");
  IElementType LITERAL_TABLE_TYPE = new LuaCatsElementType("LITERAL_TABLE_TYPE");
  IElementType LITERAL_TYPE = new LuaCatsElementType("LITERAL_TYPE");
  IElementType META_TAG = new LuaCatsElementType("META_TAG");
  IElementType MODULE_TAG = new LuaCatsElementType("MODULE_TAG");
  IElementType NAMED_TYPE = new LuaCatsElementType("NAMED_TYPE");
  IElementType NODISCARD_TAG = new LuaCatsElementType("NODISCARD_TAG");
  IElementType OPERATOR_SIGNATURE = new LuaCatsElementType("OPERATOR_SIGNATURE");
  IElementType OPERATOR_TAG = new LuaCatsElementType("OPERATOR_TAG");
  IElementType OVERLOAD_FUNCTION_SIGNATURE = new LuaCatsElementType("OVERLOAD_FUNCTION_SIGNATURE");
  IElementType OVERLOAD_TAG = new LuaCatsElementType("OVERLOAD_TAG");
  IElementType PACKAGE_TAG = new LuaCatsElementType("PACKAGE_TAG");
  IElementType PARAMETERIZED_NAME = new LuaCatsElementType("PARAMETERIZED_NAME");
  IElementType PARAMETER_NAME = new LuaCatsElementType("PARAMETER_NAME");
  IElementType PARAM_TAG = new LuaCatsElementType("PARAM_TAG");
  IElementType PARENT_TYPES = new LuaCatsElementType("PARENT_TYPES");
  IElementType PRIVATE_TAG = new LuaCatsElementType("PRIVATE_TAG");
  IElementType PROTECTED_TAG = new LuaCatsElementType("PROTECTED_TAG");
  IElementType RETURN_TAG = new LuaCatsElementType("RETURN_TAG");
  IElementType SEE_TAG = new LuaCatsElementType("SEE_TAG");
  IElementType SOURCE_TAG = new LuaCatsElementType("SOURCE_TAG");
  IElementType TABLE_LITERAL_ENTRY = new LuaCatsElementType("TABLE_LITERAL_ENTRY");
  IElementType TUPLE_TYPE = new LuaCatsElementType("TUPLE_TYPE");
  IElementType TYPE = new LuaCatsElementType("TYPE");
  IElementType TYPE_OPTION = new LuaCatsElementType("TYPE_OPTION");
  IElementType TYPE_PARAM = new LuaCatsElementType("TYPE_PARAM");
  IElementType TYPE_TAG = new LuaCatsElementType("TYPE_TAG");
  IElementType UNION_TYPE = new LuaCatsElementType("UNION_TYPE");
  IElementType VARARG_TAG = new LuaCatsElementType("VARARG_TAG");
  IElementType VERSION_COMPARISON = new LuaCatsElementType("VERSION_COMPARISON");
  IElementType VERSION_NUMBER = new LuaCatsElementType("VERSION_NUMBER");
  IElementType VERSION_SPEC = new LuaCatsElementType("VERSION_SPEC");
  IElementType VERSION_SPECS = new LuaCatsElementType("VERSION_SPECS");
  IElementType VERSION_TAG = new LuaCatsElementType("VERSION_TAG");

  IElementType CODE = new LuaCatsElementType("CODE");
  IElementType DASHES = new LuaCatsElementType("DASHES");
  IElementType KEYWORD = new LuaCatsElementType("KEYWORD");
  IElementType NAME = new LuaCatsElementType("NAME");
  IElementType NUMBER = new LuaCatsElementType("NUMBER");
  IElementType STRING = new LuaCatsElementType("STRING");
  IElementType SYMBOL = new LuaCatsElementType("SYMBOL");
  IElementType TAG = new LuaCatsElementType("TAG");
  IElementType TEXT = new LuaCatsElementType("zzz");
  IElementType ___ = new LuaCatsElementType("]*\\");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ADD_OR_SUBTRACT) {
        return new LuaCatsAddOrSubtractImpl(node);
      }
      else if (type == ALIAS_TAG) {
        return new LuaCatsAliasTagImpl(node);
      }
      else if (type == ARG_KEYWORD) {
        return new LuaCatsArgKeywordImpl(node);
      }
      else if (type == ARG_NAME) {
        return new LuaCatsArgNameImpl(node);
      }
      else if (type == ARG_SYMBOL) {
        return new LuaCatsArgSymbolImpl(node);
      }
      else if (type == ARG_TYPE) {
        return new LuaCatsArgTypeImpl(node);
      }
      else if (type == ARG_VALUE) {
        return new LuaCatsArgValueImpl(node);
      }
      else if (type == ARRAY_TYPE) {
        return new LuaCatsArrayTypeImpl(node);
      }
      else if (type == ASYNC_TAG) {
        return new LuaCatsAsyncTagImpl(node);
      }
      else if (type == BUILTIN_TYPE) {
        return new LuaCatsBuiltinTypeImpl(node);
      }
      else if (type == CAST_MODIFIER) {
        return new LuaCatsCastModifierImpl(node);
      }
      else if (type == CAST_TAG) {
        return new LuaCatsCastTagImpl(node);
      }
      else if (type == CLASS_TAG) {
        return new LuaCatsClassTagImpl(node);
      }
      else if (type == COMMENT) {
        return new LuaCatsCommentImpl(node);
      }
      else if (type == DEPRECATED_TAG) {
        return new LuaCatsDeprecatedTagImpl(node);
      }
      else if (type == DESCRIPTION) {
        return new LuaCatsDescriptionImpl(node);
      }
      else if (type == DIAGNOSTICS) {
        return new LuaCatsDiagnosticsImpl(node);
      }
      else if (type == DIAGNOSTIC_STATE) {
        return new LuaCatsDiagnosticStateImpl(node);
      }
      else if (type == DIAGNOSTIC_TAG) {
        return new LuaCatsDiagnosticTagImpl(node);
      }
      else if (type == DICTIONARY_TYPE) {
        return new LuaCatsDictionaryTypeImpl(node);
      }
      else if (type == DISTINCT_TYPE) {
        return new LuaCatsDistinctTypeImpl(node);
      }
      else if (type == ENUM_TAG) {
        return new LuaCatsEnumTagImpl(node);
      }
      else if (type == FIELD_DESCRIPTOR) {
        return new LuaCatsFieldDescriptorImpl(node);
      }
      else if (type == FIELD_KEY_DESCRIPTOR) {
        return new LuaCatsFieldKeyDescriptorImpl(node);
      }
      else if (type == FIELD_NAME_DESCRIPTOR) {
        return new LuaCatsFieldNameDescriptorImpl(node);
      }
      else if (type == FIELD_SCOPE) {
        return new LuaCatsFieldScopeImpl(node);
      }
      else if (type == FIELD_TAG) {
        return new LuaCatsFieldTagImpl(node);
      }
      else if (type == FUNCTION_SIGNATURE_ARGUMENT) {
        return new LuaCatsFunctionSignatureArgumentImpl(node);
      }
      else if (type == FUNCTION_SIGNATURE_RETURN_TYPE) {
        return new LuaCatsFunctionSignatureReturnTypeImpl(node);
      }
      else if (type == FUNCTION_SIGNATURE_TYPE) {
        return new LuaCatsFunctionSignatureTypeImpl(node);
      }
      else if (type == GENERIC_TAG) {
        return new LuaCatsGenericTagImpl(node);
      }
      else if (type == GENERIC_TYPE) {
        return new LuaCatsGenericTypeImpl(node);
      }
      else if (type == GENERIC_TYPE_PARAM) {
        return new LuaCatsGenericTypeParamImpl(node);
      }
      else if (type == GENERIC_TYPE_PARAMS) {
        return new LuaCatsGenericTypeParamsImpl(node);
      }
      else if (type == LITERAL_TABLE_TYPE) {
        return new LuaCatsLiteralTableTypeImpl(node);
      }
      else if (type == LITERAL_TYPE) {
        return new LuaCatsLiteralTypeImpl(node);
      }
      else if (type == META_TAG) {
        return new LuaCatsMetaTagImpl(node);
      }
      else if (type == MODULE_TAG) {
        return new LuaCatsModuleTagImpl(node);
      }
      else if (type == NAMED_TYPE) {
        return new LuaCatsNamedTypeImpl(node);
      }
      else if (type == NODISCARD_TAG) {
        return new LuaCatsNodiscardTagImpl(node);
      }
      else if (type == OPERATOR_SIGNATURE) {
        return new LuaCatsOperatorSignatureImpl(node);
      }
      else if (type == OPERATOR_TAG) {
        return new LuaCatsOperatorTagImpl(node);
      }
      else if (type == OVERLOAD_FUNCTION_SIGNATURE) {
        return new LuaCatsOverloadFunctionSignatureImpl(node);
      }
      else if (type == OVERLOAD_TAG) {
        return new LuaCatsOverloadTagImpl(node);
      }
      else if (type == PACKAGE_TAG) {
        return new LuaCatsPackageTagImpl(node);
      }
      else if (type == PARAMETERIZED_NAME) {
        return new LuaCatsParameterizedNameImpl(node);
      }
      else if (type == PARAMETER_NAME) {
        return new LuaCatsParameterNameImpl(node);
      }
      else if (type == PARAM_TAG) {
        return new LuaCatsParamTagImpl(node);
      }
      else if (type == PARENT_TYPES) {
        return new LuaCatsParentTypesImpl(node);
      }
      else if (type == PRIVATE_TAG) {
        return new LuaCatsPrivateTagImpl(node);
      }
      else if (type == PROTECTED_TAG) {
        return new LuaCatsProtectedTagImpl(node);
      }
      else if (type == RETURN_TAG) {
        return new LuaCatsReturnTagImpl(node);
      }
      else if (type == SEE_TAG) {
        return new LuaCatsSeeTagImpl(node);
      }
      else if (type == SOURCE_TAG) {
        return new LuaCatsSourceTagImpl(node);
      }
      else if (type == TABLE_LITERAL_ENTRY) {
        return new LuaCatsTableLiteralEntryImpl(node);
      }
      else if (type == TUPLE_TYPE) {
        return new LuaCatsTupleTypeImpl(node);
      }
      else if (type == TYPE) {
        return new LuaCatsTypeImpl(node);
      }
      else if (type == TYPE_OPTION) {
        return new LuaCatsTypeOptionImpl(node);
      }
      else if (type == TYPE_PARAM) {
        return new LuaCatsTypeParamImpl(node);
      }
      else if (type == TYPE_TAG) {
        return new LuaCatsTypeTagImpl(node);
      }
      else if (type == UNION_TYPE) {
        return new LuaCatsUnionTypeImpl(node);
      }
      else if (type == VARARG_TAG) {
        return new LuaCatsVarargTagImpl(node);
      }
      else if (type == VERSION_COMPARISON) {
        return new LuaCatsVersionComparisonImpl(node);
      }
      else if (type == VERSION_NUMBER) {
        return new LuaCatsVersionNumberImpl(node);
      }
      else if (type == VERSION_SPEC) {
        return new LuaCatsVersionSpecImpl(node);
      }
      else if (type == VERSION_SPECS) {
        return new LuaCatsVersionSpecsImpl(node);
      }
      else if (type == VERSION_TAG) {
        return new LuaCatsVersionTagImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
