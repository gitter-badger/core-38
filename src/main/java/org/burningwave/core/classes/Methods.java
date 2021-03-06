/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.LowLevelObjectsHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.function.Executor;
import org.burningwave.core.function.ThrowingFunction;

@SuppressWarnings("unchecked")
public class Methods extends Members.Handler.OfExecutable<Method, MethodCriteria> {
	
	public static Methods create() {
		return new Methods();
	}

	String createGetterMethodNameByPropertyName(String property) {
		String methodName = 
			"get" + Strings.capitalizeFirstCharacter(property);
		return methodName;
	}

	String createSetterMethodNameByPropertyName(String property) {
		String methodName = 
			"set" + Strings.capitalizeFirstCharacter(property);
		return methodName;
	}
	
	public Method findOneAndMakeItAccessible(Class<?> targetClass, String memberName, Class<?>... argumentTypes) {
		Collection<Method> members = findAllByExactNameAndMakeThemAccessible(targetClass, memberName, argumentTypes);
		if (members.size() == 1) {
			return members.stream().findFirst().get();
		} else if (members.size() > 1) {
			Collection<Method> membersThatMatch = searchForExactMatch(members, argumentTypes);
			if (membersThatMatch.size() == 1) {
				return membersThatMatch.stream().findFirst().get();
			}
		}
		return Throwables.throwException("Method {} not found or found more than one method in {} hierarchy", memberName, targetClass.getName());
	}
	
	public Method findFirstAndMakeItAccessible(Class<?> targetClass, String memberName, Class<?>... arguments) {
		Collection<Method> members = findAllByExactNameAndMakeThemAccessible(targetClass, memberName, arguments);
		if (members.size() == 1) {
			return members.stream().findFirst().get();
		} else if (members.size() > 1) {
			Collection<Method> membersThatMatch = searchForExactMatch(members, arguments);
			if (!membersThatMatch.isEmpty()) {
				return membersThatMatch.stream().findFirst().get();
			}
			return members.stream().findFirst().get();
		}
		return Throwables.throwException("Method {} not found in {} hierarchy", memberName, targetClass.getName());
	}
	
	public Collection<Method> findAllByExactNameAndMakeThemAccessible(
		Class<?> targetClass,
		String methodName,
		Class<?>... argumentTypes
	) {	
		return findAllByNamePredicateAndMakeThemAccessible(targetClass, "equals " + methodName, methodName::equals, argumentTypes);
	}
	
	public Collection<Method> findAllByMatchedNameAndMakeThemAccessible(
		Class<?> targetClass,
		String methodName,
		Class<?>... argumentTypes
	) {	
		return findAllByNamePredicateAndMakeThemAccessible(targetClass, "match " + methodName, methodName::matches, argumentTypes);
	}
	
	private Collection<Method> findAllByNamePredicateAndMakeThemAccessible(
		Class<?> targetClass,
		String cacheKeyPrefix,
		Predicate<String> namePredicate,
		Class<?>... arguments
	) {	
		String cacheKey = getCacheKey(targetClass, cacheKeyPrefix, arguments);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		return Cache.uniqueKeyForMethods.getOrUploadIfAbsent(targetClassClassLoader, cacheKey, () -> {
			MethodCriteria criteria = MethodCriteria.create()
				.name(namePredicate)
				.and().parameterTypesAreAssignableFrom(arguments);			
			if (arguments != null && arguments.length == 0) {
				criteria = criteria.or(MethodCriteria.create().name(namePredicate).and().parameter((parameters, idx) -> parameters.length == 1 && parameters[0].isVarArgs()));
			}
			MethodCriteria finalCriteria = criteria;
			return Cache.uniqueKeyForMethods.getOrUploadIfAbsent(targetClassClassLoader, cacheKey, () -> 
				Collections.unmodifiableCollection(
					findAllAndMakeThemAccessible(targetClass).stream().filter(
						finalCriteria.getPredicateOrTruePredicateIfPredicateIsNull()
					).collect(
						Collectors.toCollection(LinkedHashSet::new)
					)
				)
			);
		});
	}

	public Collection<Method> findAllAndMakeThemAccessible(
		Class<?> targetClass
	) {
		String cacheKey = getCacheKey(targetClass, "all methods");
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Collection<Method> members = Cache.uniqueKeyForMethods.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, () -> {
				return Collections.unmodifiableCollection(
					findAllAndApply(
						MethodCriteria.create(), targetClass, (member) -> {
							LowLevelObjectsHandler.setAccessible(member, true);
						}
					)
				);
			}
		);
		return members;
	}

	public 	<T> T invokeStatic(Class<?> targetClass, String methodName, Object... arguments) {
		return invoke(
			targetClass, null, methodName, method -> 
				(T)method.invoke(null, 
					getArgumentArray(
						method,
						this::getArgumentListWithArrayForVarArgs,
						ArrayList::new, 
						arguments
					)
				),
			arguments
		);
	}
	
	public <T> T invoke(Object target, String methodName, Object... arguments) {
		return invoke(
			Classes.retrieveFrom(target), 
			null, methodName, method -> 
				(T)method.invoke(
					target,
					getArgumentArray(
						method,
						this::getArgumentListWithArrayForVarArgs,
						ArrayList::new, 
						arguments
					)
				),
			arguments
		);
	}
	
	private <T> T invoke(Class<?> targetClass, Object target, String methodName, ThrowingFunction<Method, T, Throwable> methodInvoker, Object... arguments) {
		return Executor.get(() ->methodInvoker.apply(findFirstAndMakeItAccessible(targetClass, methodName, Classes.retrieveFrom(arguments))));
	}
	
	public 	<T> T invokeStaticDirect(Class<?> targetClass, String methodName, Object... arguments) {
		return (T) invokeDirect(targetClass, null, methodName, ArrayList::new, arguments);
	}
	
	public <T> T invokeDirect(Object target, String methodName, Object... arguments) {
		return (T) invokeDirect(
			Classes.retrieveFrom(target), 
			target, methodName, () -> {
				List<Object> argumentList = new ArrayList<>();
				argumentList.add(target);
				return argumentList;
			},
			arguments
		);
	}
	
	private <T> T invokeDirect(Class<?> targetClass, Object target, String methodName, Supplier<List<Object>> listSupplier,  Object... arguments) {
		Class<?>[] argsType = Classes.retrieveFrom(arguments);
		String cacheKey = getCacheKey(targetClass, "equals " + methodName, argsType);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Entry<Executable, MethodHandle> methodHandleBag = Cache.uniqueKeyForExecutableAndMethodHandle.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, 
			() -> {
				Method method = findFirstAndMakeItAccessible(targetClass, methodName, argsType);
				return new AbstractMap.SimpleEntry<>(
					method, 
					convertToMethodHandle(
						method
					)
				);
			}
		);
		return Executor.get(() -> {
				Method method = (Method)methodHandleBag.getKey();
				List<Object> argumentList = getFlatArgumentList(method, listSupplier, arguments);
				return (T)methodHandleBag.getValue().invokeWithArguments(argumentList);
			}
		);
	}
	
	public MethodHandle convertToMethodHandle(Method method) {
		return convertToMethodHandleBag(method).getValue();
	}
	
	public Map.Entry<Lookup, MethodHandle> convertToMethodHandleBag(Method method) {
		try {
			Class<?> methodDeclaringClass = method.getDeclaringClass();
			MethodHandles.Lookup consulter = LowLevelObjectsHandler.getConsulter(methodDeclaringClass);
			return new AbstractMap.SimpleEntry<>(consulter,
				!Modifier.isStatic(method.getModifiers())?
					consulter.findSpecial(
						methodDeclaringClass, method.getName(),
						MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
						methodDeclaringClass
					):
					consulter.findStatic(
						methodDeclaringClass, method.getName(),
						MethodType.methodType(method.getReturnType(), method.getParameterTypes())
					)
			);
		} catch (NoSuchMethodException | IllegalAccessException exc) {
			return Throwables.throwException(exc);
		}
	}
	
}
