/*
 * Copyright 2016 Federico Tomassetti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.javaparser.symbolsolver.core.resolution;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.contexts.AbstractJavaParserContext;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.resolution.Value;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Context is very similar to scope.
 * In the context we look for solving symbols.
 *
 * @author Federico Tomassetti
 */
public interface Context {

    Context getParent();

    /* Type resolution */

    default Optional<ResolvedType> solveGenericType(String name, TypeSolver typeSolver) {
        return Optional.empty();
    }

    default SymbolReference<ResolvedTypeDeclaration> solveType(String name, TypeSolver typeSolver) {
        Context parent = getParent();
        if (parent == null) {
            return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
        } else {
            return parent.solveType(name, typeSolver);
        }
    }

    /* Symbol resolution */

    SymbolReference<? extends ResolvedValueDeclaration> solveSymbol(String name, TypeSolver typeSolver);

    default Optional<Value> solveSymbolAsValue(String name, TypeSolver typeSolver) {
        SymbolReference<? extends ResolvedValueDeclaration> ref = solveSymbol(name, typeSolver);
        if (ref.isSolved()) {
            Value value = Value.from(ref.getCorrespondingDeclaration());
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }

    /* Constructor resolution */

    /**
     * We find the method declaration which is the best match for the given name and list of typeParametersValues.
     */
    default SymbolReference<ResolvedConstructorDeclaration> solveConstructor(List<ResolvedType> argumentsTypes, TypeSolver typeSolver) {
        throw new IllegalArgumentException("Constructor resolution is available only on Class Context");
    }

    /* Methods resolution */

    /**
     * We find the method declaration which is the best match for the given name and list of typeParametersValues.
     */
    SymbolReference<ResolvedMethodDeclaration> solveMethod(String name, List<ResolvedType> argumentsTypes, boolean staticOnly, TypeSolver typeSolver);

    /**
     * Similar to solveMethod but we return a MethodUsage. A MethodUsage corresponds to a MethodDeclaration plus the
     * resolved type variables.
     */
    default Optional<MethodUsage> solveMethodAsUsage(String name, List<ResolvedType> argumentsTypes, TypeSolver typeSolver) {
        SymbolReference<ResolvedMethodDeclaration> methodSolved = solveMethod(name, argumentsTypes, false, typeSolver);
        if (methodSolved.isSolved()) {
            ResolvedMethodDeclaration methodDeclaration = methodSolved.getCorrespondingDeclaration();
            MethodUsage methodUsage = ContextHelper.resolveTypeVariables(this, methodDeclaration, argumentsTypes);//methodDeclaration.resolveTypeVariables(this, argumentsTypes);
            return Optional.of(methodUsage);
        } else {
            return Optional.empty();
        }
    }

    /**
     * The local variables that are declared and made visible to a given child.
     *
     * @param child
     * @return
     */
    default List<VariableDeclarator> localVariablesExposedToChild(Node child) {
        //throw new UnsupportedOperationException(this.getClass().getCanonicalName());
        // TODO fixme
        return Collections.emptyList();
    }

    default List<Parameter> parametersExposedToChild(Node child) {
        //throw new UnsupportedOperationException(this.getClass().getCanonicalName());
        // TODO fixme
        return Collections.emptyList();
    }


    /**
     * The local variables that are visible in a certain scope are defined in JLS 6.3. Scope of a Declaration.
     *
     * 1. The scope of a local variable declaration in a block (§14.4) is the rest of the block in which the declaration
     * appears, starting with its own initializer and including any further declarators to the right in the local
     * variable declaration statement.
     *
     * 2. The scope of a local variable declared in the ForInit part of a basic for statement (§14.14.1) includes all
     * of the following:
     * 2.1 Its own initializer
     * 2.2 Any further declarators to the right in the ForInit part of the for statement
     * 2.3 The Expression and ForUpdate parts of the for statement
     * 2.4 The contained Statement
     *
     * 3. The scope of a local variable declared in the FormalParameter part of an enhanced for statement (§14.14.2) is
     * the contained Statement.
     * 4. The scope of a parameter of an exception handler that is declared in a catch clause of a try statement
     * (§14.20) is the entire block associated with the catch.
     *
     * 5. The scope of a variable declared in the ResourceSpecification of a try-with-resources statement (§14.20.3) is
     * from the declaration rightward over the remainder of the ResourceSpecification and the entire try block
     * associated with the try-with-resources statement.
     */
    default Optional<VariableDeclarator> localVariableDeclarationInScope(String name) {
        // Normally we see all the variables declared in the parent, however there are some exceptions
        //
        // In the case of a statement we see all the variables declared before
        //if (localVariablesDeclared)
        // TODO SymbolDeclarator symbolDeclarator = JavaParserFactory.getSymbolDeclarator(wrappedNode, typeSolver);
        // TODO getSymbolDeclarationsRecursively
        //throw new UnsupportedOperationException();

        if (getParent() == null) {
            return Optional.empty();
        }
        Optional<VariableDeclarator> localRes = getParent().localVariablesExposedToChild(((AbstractJavaParserContext)this)
                .getWrappedNode()).stream().filter(vd -> vd.getNameAsString().equals(name)).findFirst();
        if (localRes.isPresent()) {
            return localRes;
        }

        return getParent().localVariableDeclarationInScope(name);
    }

    default Optional<Parameter> parameterDeclarationInScope(String name) {
        if (getParent() == null) {
            return Optional.empty();
        }
        Optional<Parameter> localRes = getParent().parametersExposedToChild(((AbstractJavaParserContext)this)
                .getWrappedNode()).stream().filter(vd -> vd.getNameAsString().equals(name)).findFirst();
        if (localRes.isPresent()) {
            return localRes;
        }

        return getParent().parameterDeclarationInScope(name);
    }

    default Optional<VariableDeclarator> fieldDeclarationInScope(String name) {
        //throw new UnsupportedOperationException();
        // TODO Fixme
        return Optional.empty();
    }
}
