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
import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.LowLevelObjectsHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Members;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.FileSystemItem;

@SuppressWarnings("unchecked")
public class Classes implements Component, MembersRetriever {
	public static class Symbol{
		public static class Tag {
			static final byte UTF8 = 1;
			static final byte INTEGER = 3;
			static final byte FLOAT = 4;
			static final byte LONG = 5;
			static final byte DOUBLE = 6;
			static final byte CLASS = 7;
			static final byte STRING = 8;
			static final byte FIELD_REF = 9;
			static final byte METHOD_REF = 10;
			static final byte INTERFACE_METHOD_REF = 11;
			static final byte NAME_AND_TYPE = 12;
			static final byte METHOD_HANDLE = 15;
			static final byte METHOD_TYPE = 16;
			static final byte DYNAMIC = 17;
			static final byte INVOKE_DYNAMIC = 18;
			static final byte MODULE = 19;
			static final byte PACKAGE = 20;

	    }		
	}
	
	private Classes() {}
	
	public static Classes create() {
		return new Classes();
	}
	
	public <T> Class<T> retrieveFrom(Object object) {
		return object != null ? (Class<T>)object.getClass() : null;
	}
	
	public Class<?>[] retrieveFrom(Object... objects) {
		Class<?>[] classes = null;
		if (objects != null) {
			classes = new Class[objects.length];
			for (int i = 0; i < objects.length; i++) {
				if (objects[i] != null) {
					classes[i] = retrieveFrom(objects[i]);
				}
			}
		} else {
			classes = new Class[]{null};
		}
		return classes;
	}
	
	public String retrieveName(Throwable exc) {
		String className = exc.getMessage();
		if (className != null) {
			if (className.contains("Could not initialize class ")) {
				className = className.replace("Could not initialize class ", "");
			}
			if (className.contains("NoClassDefFoundError: ")) {
				className = className.substring(className.lastIndexOf("NoClassDefFoundError: ") + "NoClassDefFoundError: ".length());
			}
			if (className.contains("class: ")) {
				className = className.substring(className.lastIndexOf("class: ") + "class: ".length());
			}
			return className.contains(" ")? null : className.replace("/", ".");
		}
		return className;
	}
	
	public Collection<String> retrieveNames(Throwable exc) {
		Collection<String> classesName = new LinkedHashSet<>();
		Optional.ofNullable(retrieveName(exc)).map(classesName::add);
		if (exc.getCause() != null) {
			classesName.addAll(retrieveNames(exc.getCause()));
		}
		return classesName;
	}
	
	public String retrievePackageName(String className) {
		String packageName = null;
		if (className.contains(("."))) {
			packageName = className.substring(0, className.lastIndexOf("."));
		}
		return packageName;
	}
	
	public String retrieveSimpleName(String className) {
		String classSimpleName = null;
		if (className.contains(("."))) {
			classSimpleName = className.substring(className.lastIndexOf(".")+1);
		} else {
			classSimpleName = className;
		}
		if (classSimpleName.contains("$")) {
			classSimpleName = classSimpleName.substring(classSimpleName.lastIndexOf("$")+1);
		}
		return classSimpleName;
	}
	
	public String toPath(Class<?> cls) {
		String path = cls.getSimpleName().replace(".", "$");
		Package pckg = cls.getPackage();
		if (pckg != null) {
			path = pckg.getName().replace(".", "/") + "/" + path + ".class";
		}
		return path;
	}
	
	public String toPath(String className) {
		return className.replace(".", "/");
	}
	
	public String retrieveName(
		final byte[] classFileBuffer
	) {
		return retrieveName((index) -> classFileBuffer[index]);
	}
	
	public String retrieveName(
		final ByteBuffer classFileBuffer
	) {
		return retrieveName(classFileBuffer::get);
	}
	
	private String retrieveName(
		final Function<Integer, Byte> byteSupplier
	) {
		int classFileOffset = 0;
		int constantPoolCount = readUnsignedShort(byteSupplier, classFileOffset + 8);
		int[] cpInfoOffsets = new int[constantPoolCount];
		String[] constantUtf8Values = new String[constantPoolCount];
		int currentCpInfoIndex = 1;
		int currentCpInfoOffset = classFileOffset + 10;
		int currentMaxStringLength = 0;
		while (currentCpInfoIndex < constantPoolCount) {
			cpInfoOffsets[currentCpInfoIndex++] = currentCpInfoOffset + 1;
			int cpInfoSize;
			byte currentCpInfoValue = byteSupplier.apply(currentCpInfoOffset);
			if (currentCpInfoValue == Symbol.Tag.INTEGER ||
				currentCpInfoValue == Symbol.Tag.FLOAT ||
				currentCpInfoValue == Symbol.Tag.FIELD_REF ||
				currentCpInfoValue == Symbol.Tag.METHOD_REF ||
				currentCpInfoValue == Symbol.Tag.INTERFACE_METHOD_REF ||
				currentCpInfoValue == Symbol.Tag.NAME_AND_TYPE ||
				currentCpInfoValue == Symbol.Tag.DYNAMIC ||
				currentCpInfoValue == Symbol.Tag.INVOKE_DYNAMIC
			) {
				cpInfoSize = 5;
			} else if (currentCpInfoValue == Symbol.Tag.LONG ||
				currentCpInfoValue == Symbol.Tag.DOUBLE
			) {
				cpInfoSize = 9;
				currentCpInfoIndex++;
			} else if (currentCpInfoValue == Symbol.Tag.UTF8) {
				cpInfoSize = 3 + readUnsignedShort(byteSupplier, currentCpInfoOffset + 1);
				if (cpInfoSize > currentMaxStringLength) {
					currentMaxStringLength = cpInfoSize;
				}
			} else if (currentCpInfoValue == Symbol.Tag.METHOD_HANDLE) {
				cpInfoSize = 4;
			} else if (currentCpInfoValue == Symbol.Tag.CLASS ||
				currentCpInfoValue == Symbol.Tag.STRING ||
				currentCpInfoValue == Symbol.Tag.METHOD_TYPE ||
				currentCpInfoValue == Symbol.Tag.MODULE ||
				currentCpInfoValue == Symbol.Tag.PACKAGE			
			) {
				cpInfoSize = 3;
			} else {
				throw new IllegalArgumentException();
			}
			currentCpInfoOffset += cpInfoSize;
		}
		int maxStringLength = currentMaxStringLength;
		int header = currentCpInfoOffset;
		return readUTF8(
			byteSupplier, 
			cpInfoOffsets[readUnsignedShort(byteSupplier, header + 2)], new char[maxStringLength], constantUtf8Values, cpInfoOffsets
		);
	}

	private String readUTF8(
		Function<Integer, Byte> byteSupplier,
		final int offset,
		final char[] charBuffer,
		String[] constantUtf8Values,
		int[] cpInfoOffsets
	) {
		int constantPoolEntryIndex = readUnsignedShort(byteSupplier, offset);
		if (offset == 0 || constantPoolEntryIndex == 0) {
			return null;
		}
		return readUtf(byteSupplier, constantPoolEntryIndex, charBuffer, constantUtf8Values, cpInfoOffsets);
	}

	private String readUtf(
		Function<Integer, Byte> byteSupplier,
		final int constantPoolEntryIndex,
		final char[] charBuffer,
		String[] constantUtf8Values,
		int[] cpInfoOffsets
	) {
		String value = constantUtf8Values[constantPoolEntryIndex];
		if (value != null) {
			return value;
		}
		int cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex];
		return constantUtf8Values[constantPoolEntryIndex] = readUtf(byteSupplier, cpInfoOffset + 2, readUnsignedShort(byteSupplier, cpInfoOffset),
				charBuffer);
	}

	private int readUnsignedShort(
		Function<Integer, Byte> byteSupplier,
		final int offset
	) {
		return ((byteSupplier.apply(offset) & 0xFF) << 8) | (byteSupplier.apply(offset + 1) & 0xFF);
	}

	private String readUtf(Function<Integer, Byte> byteSupplier, final int utfOffset, final int utfLength, final char[] charBuffer) {
		int currentOffset = utfOffset;
		int endOffset = currentOffset + utfLength;
		int strLength = 0;
		while (currentOffset < endOffset) {
			int currentByte = byteSupplier.apply(currentOffset++);
			if ((currentByte & 0x80) == 0) {
				charBuffer[strLength++] = (char) (currentByte & 0x7F);
			} else if ((currentByte & 0xE0) == 0xC0) {
				charBuffer[strLength++] = (char) (((currentByte & 0x1F) << 6) + (byteSupplier.apply(currentOffset++) & 0x3F));
			} else {
				charBuffer[strLength++] = (char) (((currentByte & 0xF) << 12)
						+ ((byteSupplier.apply(currentOffset++) & 0x3F) << 6) + (byteSupplier.apply(currentOffset++) & 0x3F));
			}
		}
		return new String(charBuffer, 0, strLength);
	}

	public ClassLoader getClassLoader(Class<?> cls) {
		ClassLoader clsLoader = cls.getClassLoader();
		if (clsLoader == null) {
			clsLoader = ClassLoader.getSystemClassLoader();
		}
		return clsLoader;
	}
	
	public ByteBuffer getByteCode(Class<?> cls) {
		if (cls.isPrimitive()) {
			return null;
		}
		ClassLoader clsLoader = getClassLoader(cls);
		InputStream inputStream = clsLoader.getResourceAsStream(
			cls.getName().replace(".", "/") + ".class"
		);
		return Streams.toByteBuffer(
			java.util.Objects.requireNonNull(inputStream, "Could not acquire bytecode for class " + cls.getName())
		);
	}
	
	@Override
	public Field[] getDeclaredFields(Class<?> cls)  {
		return Cache.classLoaderForFields.getOrUploadIfAbsent(
			getClassLoader(cls), cls.getName().replace(".", "/"),
			() -> LowLevelObjectsHandler.getDeclaredFields(cls)
		);
	}
	
	@Override
	public <T> Constructor<T>[] getDeclaredConstructors(Class<T> cls)  {
		return (Constructor<T>[]) Cache.classLoaderForConstructors.getOrUploadIfAbsent(
			getClassLoader(cls), cls.getName().replace(".", "/"),
			() -> LowLevelObjectsHandler.getDeclaredConstructors(cls)
		);
	}
	
	@Override
	public Method[] getDeclaredMethods(Class<?> cls)  {
		return Cache.classLoaderForMethods.getOrUploadIfAbsent(
			getClassLoader(cls), cls.getName().replace(".", "/"),
			() -> LowLevelObjectsHandler.getDeclaredMethods(cls)
		);
	}	

	public void setAccessible(AccessibleObject object, boolean flag) {
		LowLevelObjectsHandler.setAccessible(object, flag);
	}
	
	public boolean isLoadedBy(Class<?> cls, ClassLoader classLoader) {
		if (cls.getClassLoader() == classLoader) {
			return true;
		} else if (classLoader != null && classLoader.getParent() != null) {
			return isLoadedBy(cls, classLoader.getParent());
		} else {
			return false;
		}
	}
	
	public boolean isAssignableFrom(Class<?> cls_01, Class<?> cls_02) {
		return getClassOrWrapper(cls_01).isAssignableFrom(getClassOrWrapper(cls_02));
	}
	
	public Class<?> getClassOrWrapper(Class<?> cls) {
		if (cls.isPrimitive()) {
			if (cls == int.class) {
				return Integer.class;
			} else if (cls == long.class) {
				return Long.class;
			} else if (cls == float.class) {
				return Float.class;
			} else if (cls == double.class) {
				return Double.class;
			} else if (cls == boolean.class) {
				return Boolean.class;
			} else if (cls == byte.class) {
				return Byte.class;
			} else if (cls == char.class) {
				return Character.class;
			}
		}
		return cls;
	}
	
	public static class Loaders implements Component {
		protected Map<ClassLoader, Collection<Class<?>>> classLoadersClasses;
		protected Map<ClassLoader, Map<String, ?>> classLoadersPackages;
		protected Map<String, MethodHandle> classLoadersMethods;
		
		private Loaders() {
			this.classLoadersClasses = new HashMap<>();
			this.classLoadersPackages = new HashMap<>();
			this.classLoadersMethods = new HashMap<>();
		}
		
		public static Loaders create() {
			return new Loaders();
		}
		
		public Collection<ClassLoader> getAllParents(ClassLoader classLoader) {
			return getHierarchy(classLoader, false);
		}
		
		public Collection<ClassLoader> getHierarchy(ClassLoader classLoader) {
			return getHierarchy(classLoader, true);
		}
		
		private  Collection<ClassLoader> getHierarchy(ClassLoader classLoader, boolean includeClassLoader) {
			Collection<ClassLoader> classLoaders = new LinkedHashSet<>();
			if (includeClassLoader) {
				classLoaders.add(classLoader);
			}
			while ((classLoader = getParent(classLoader)) != null) {
				classLoaders.add(classLoader);
			}
			return classLoaders;
		}
		
		public Function<Boolean, ClassLoader> setAsMaster(ClassLoader classLoader, ClassLoader futureParent) {
			return LowLevelObjectsHandler.setAsParent(getMaster(classLoader), futureParent);
		}
		
		public Function<Boolean, ClassLoader> setAsParent(ClassLoader classLoader, ClassLoader futureParent) {
			return LowLevelObjectsHandler.setAsParent(classLoader, futureParent);
		}
		
		public ClassLoader getParent(ClassLoader classLoader) {
			return LowLevelObjectsHandler.getParent(classLoader);
		}
		
		public  ClassLoader getMaster(ClassLoader classLoader) {
			while (getParent(classLoader) != null) {
				classLoader = getParent(classLoader); 
			}
			return classLoader;
		}
		
		public MethodHandle getDefinePackageMethod(ClassLoader classLoader) {
			return getMethod(
				classLoader.getClass().getName() + "_" + "definePackage",
				() -> findDefinePackageMethodAndMakeItAccesible(classLoader)
			);
		}	
		
		private MethodHandle findDefinePackageMethodAndMakeItAccesible(ClassLoader classLoader) {
			Method method = Members.findAll(
				MethodCriteria.byScanUpTo((cls) -> 
					cls.getName().equals(ClassLoader.class.getName())
				).name(
					"definePackage"::equals
				).and().parameterTypesAreAssignableFrom(
					String.class, String.class, String.class, String.class,
					String.class, String.class, String.class, URL.class
				),
				classLoader.getClass()
			).stream().findFirst().orElse(null);
			return Methods.convertToMethodHandleBag(method).getValue();
		}
		
		public MethodHandle getDefineClassMethod(ClassLoader classLoader) {
			return getMethod(
				Classes.getClassLoader(classLoader.getClass()) + "_" + classLoader + "_" +  "defineClass",
				() -> findDefineClassMethodAndMakeItAccesible(classLoader)
			);
		}
		
		Object getClassLoadingLock(ClassLoader classLoader, String className) {
			try {
				return getGetClassLoadingLockMethod(classLoader).invoke(classLoader, className);
			} catch (Throwable exc) {
				return Throwables.throwException(exc);
			}
		}
		
		public MethodHandle getGetClassLoadingLockMethod(ClassLoader classLoader) {
			return getMethod(
				Classes.getClassLoader(classLoader.getClass()) + "_" + classLoader + "_" +  "getClassLoadingLock",
				() -> findGetClassLoadingLockMethodAndMakeItAccesible(classLoader)
			);
		}
		
		private MethodHandle findDefineClassMethodAndMakeItAccesible(ClassLoader classLoader) {
			Method method = Members.findAll(
				MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
					(classLoader instanceof MemoryClassLoader? "_defineClass" : "defineClass")::equals
				).and().parameterTypes(params -> 
					params.length == 3
				).and().parameterTypesAreAssignableFrom(
					String.class, ByteBuffer.class, ProtectionDomain.class
				).and().returnType((cls) -> cls.getName().equals(Class.class.getName())),
				classLoader.getClass()
			).stream().findFirst().orElse(null);
			return Methods.convertToMethodHandleBag(method).getValue();
		}
		
		private MethodHandle findGetClassLoadingLockMethodAndMakeItAccesible(ClassLoader classLoader) {
			Method method = Members.findAll(
				MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
					"getClassLoadingLock"::equals
				).and().parameterTypes(params -> 
					params.length == 1
				).and().parameterTypesAreAssignableFrom(
					String.class
				),
				classLoader.getClass()
			).stream().findFirst().orElse(null);
			return Methods.convertToMethodHandleBag(method).getValue();
		}
		
		private MethodHandle getMethod(String key, Supplier<MethodHandle> methodSupplier) {
			MethodHandle method = classLoadersMethods.get(key);
			if (method == null) {
				synchronized (classLoadersMethods) {
					method = classLoadersMethods.get(key);
					if (method == null) {
						classLoadersMethods.put(key, method = methodSupplier.get());
					}
				}
			}
			return method;
		}
		
		public Collection<Class<?>> retrieveLoadedClasses(ClassLoader classLoader) {
			Collection<Class<?>> classes = classLoadersClasses.get(classLoader);
			if (classes != null) {
				return classes;
			} else {
				classes = classLoadersClasses.get(classLoader);
				if (classes == null) {
					synchronized (classLoadersClasses) {
						classes = classLoadersClasses.get(classLoader);
						if (classes == null) {
							classLoadersClasses.put(classLoader, (classes = LowLevelObjectsHandler.retrieveLoadedClasses(classLoader)));
							return classes;
						}
					}
				}
			}
			logWarn("'classes' collection has not been initialized on {}: trying recursive call", classLoader);
			return retrieveLoadedClasses(classLoader);
		}
		
		public Collection<Class<?>> retrieveAllLoadedClasses(ClassLoader classLoader) {
			Collection<Class<?>> allLoadedClasses = new LinkedHashSet<>();
			allLoadedClasses.addAll(retrieveLoadedClasses(classLoader));
			if (classLoader.getParent() != null) {
				allLoadedClasses.addAll(retrieveAllLoadedClasses(classLoader.getParent()));
			}
			return allLoadedClasses;
		}
		
		public Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader) {
			Map<String, ?> packages = classLoadersPackages.get(classLoader);
			if (packages == null) {
				synchronized (classLoadersPackages) {
					packages = classLoadersPackages.get(classLoader);
					if (packages == null) {
						classLoadersPackages.put(classLoader, (packages = LowLevelObjectsHandler.retrieveLoadedPackages(classLoader)));
					}
				}
			
			}
			if (packages == null) {
				Throwables.throwException("Could not find packages Map on {}", classLoader);
			}
			return packages;
			
		}
		
		public Package retrieveLoadedPackage(ClassLoader classLoader, Object packageToFind, String packageName) {
			return Executor.get(() -> LowLevelObjectsHandler.retrieveLoadedPackage(classLoader, packageToFind, packageName));
		}
		
		public <T> Class<T> loadOrDefineByJavaClass(
			JavaClass javaClass,
			ClassLoader classLoader
		) throws ClassNotFoundException {
			Map<String, JavaClass> repository = new HashMap<>();
			repository.put(javaClass.getName(), javaClass);
			return loadOrDefineByJavaClass(javaClass.getName(), repository, classLoader);
		}

		
		public <T> Class<T> loadOrDefineByJavaClass(
			String className,
			Map<String, JavaClass> byteCodes,
			ClassLoader classLoader
		) throws ClassNotFoundException {
			if (!(classLoader instanceof MemoryClassLoader)) {
				return loadOrDefineByByteCode(
					className, clsName -> byteCodes.get(clsName).getByteCode(), classLoader,
					getDefineClassMethod(classLoader), getDefinePackageMethod(classLoader)
				);
			} else {
				for (Map.Entry<String, JavaClass> clazz : byteCodes.entrySet()) {
					((MemoryClassLoader)classLoader).addByteCode(
						clazz.getKey(), clazz.getValue().getByteCode()
					);
				}
				return (Class<T>) classLoader.loadClass(className);
			}
		}
		
		public Class<?> loadOrDefineByByteCode(ByteBuffer byteCode, ClassLoader classLoader) throws ClassNotFoundException {
			Map<String, JavaClass> repository = new HashMap<>();
			return JavaClass.extractByUsing(byteCode, javaClass -> {
				repository.put(javaClass.getName(), javaClass);
				return loadOrDefineByJavaClass(javaClass.getName(), repository, classLoader);
			});
		}
		
		public <T> Class<T> loadOrDefineByByteCode(
			String className,
			Map<String, ByteBuffer> repository,
			ClassLoader classLoader
		) throws ClassNotFoundException {
			if (!(classLoader instanceof MemoryClassLoader)) {
				return loadOrDefineByByteCode(
					className, clsName -> repository.get(clsName), classLoader,
					getDefineClassMethod(classLoader), getDefinePackageMethod(classLoader)
				);
			} else {
				for (Map.Entry<String, ByteBuffer> clazz : repository.entrySet()) {
					((MemoryClassLoader)classLoader).addByteCode(
						clazz.getKey(), clazz.getValue()
					);
				}
				return (Class<T>) classLoader.loadClass(className);
			}
		}
		
		
		private <T> Class<T> loadOrDefineByByteCode(
			String className, 
			Function<String, ByteBuffer> byteCodeSupplier,
			ClassLoader classLoader,
			MethodHandle defineClassMethod, 
			MethodHandle definePackageMethod
		) throws ClassNotFoundException {
			try {
				try {
					return (Class<T>) classLoader.loadClass(className);
				}  catch (ClassNotFoundException | NoClassDefFoundError exc) {
					Class<T> cls = defineOrLoad(classLoader, defineClassMethod, className, byteCodeSupplier.apply(className));
	    			definePackageFor(cls, classLoader, definePackageMethod);
	    			return cls;
				}
			}  catch (ClassNotFoundException | NoClassDefFoundError | InvocationTargetException exc) {
				if (byteCodeSupplier.apply(className) == null) {
					throw new ClassNotFoundException(className);
				}
				String newNotFoundClassName = Classes.retrieveNames(exc).stream().findFirst().orElseGet(() -> null);
				loadOrDefineByByteCode(
					newNotFoundClassName,
					byteCodeSupplier, classLoader, defineClassMethod, definePackageMethod
        		);
				return loadOrDefineByByteCode(className, byteCodeSupplier,
					classLoader,
					defineClassMethod, definePackageMethod
        		);
			}
	    }
		
		public <T> Class<T> loadOrDefine(
			Class<T> toLoad, 
			ClassLoader classLoader
		) throws ClassNotFoundException {
			return loadOrDefine(
				toLoad, classLoader,
				getDefineClassMethod(classLoader),
				getDefinePackageMethod(classLoader)
			);
		}
		
		private <T> Class<T> loadOrDefine(
			Class<T> toLoad, 
			ClassLoader classLoader, 
			MethodHandle defineClassMethod, 
			MethodHandle definePackageMethod
		) throws ClassNotFoundException {
			String className = toLoad.getName();
			try {
				try {
					return (Class<T>)classLoader.loadClass(className);
				} catch (ClassNotFoundException | NoClassDefFoundError exc) {
					Class<T> cls = defineOrLoad(classLoader, defineClassMethod, className, Streams.shareContent(Classes.getByteCode(toLoad)));
	    			definePackageFor(cls, classLoader, definePackageMethod);
	    			return cls;
				}
			} catch (ClassNotFoundException | NoClassDefFoundError | InvocationTargetException exc) {
				String newNotFoundClassName = Classes.retrieveNames(exc).stream().findFirst().orElseGet(() -> null);
				loadOrDefine(
        			Class.forName(
        				newNotFoundClassName, false, toLoad.getClassLoader()
        			),
        			classLoader, defineClassMethod, definePackageMethod
        		);
				return (Class<T>)loadOrDefine(
        			Class.forName(
        				className, false, toLoad.getClassLoader()
        			),
        			classLoader, defineClassMethod, definePackageMethod
        		);
			}
	    }
		
		public <T> Class<T> defineOrLoad(ClassLoader classLoader, JavaClass javaClass) throws ReflectiveOperationException {
			String className = javaClass.getName();
			Class<T> definedClass = defineOrLoad(classLoader, getDefineClassMethod(classLoader), className, javaClass.getByteCode());
			definePackageFor(definedClass, classLoader, getDefinePackageMethod(classLoader));
			return definedClass;			
		}
		

		private <T> Class<T> defineOrLoad(
			ClassLoader classLoader, 
			MethodHandle method, 
			String className,
			ByteBuffer byteCode
		) throws ClassNotFoundException, InvocationTargetException, NoClassDefFoundError {
			try {
				synchronized (getClassLoadingLock(classLoader, className)) {
					return (Class<T>)method.invoke(classLoader, className, byteCode, null);
				}
			} catch (InvocationTargetException | ClassNotFoundException | NoClassDefFoundError exc) {
				throw exc;
			} catch (java.lang.LinkageError exc) {
				logWarn("Class {} is already defined", className);
				return (Class<T>)classLoader.loadClass(className);
			} catch (Throwable exc) {
				if (byteCode == null) {
					throw new ClassNotFoundException(className);
				}
				return Throwables.throwException(exc);
			}
		}
		
	    private Package definePackage(
			ClassLoader classLoader, MethodHandle definePackageMethod,
			String name, String specTitle, String specVersion,
			String specVendor, String implTitle, String implVersion,
			String implVendor,
			URL sealBase
		) throws IllegalArgumentException {
	    	return Executor.get(() -> {
	    		try {
	    			return (Package) definePackageMethod.invoke(classLoader, name, specTitle, specVersion, specVendor, implTitle,
	    				implVersion, implVendor, sealBase);
	    		} catch (IllegalArgumentException exc) {
	    			logWarn("Package " + name + " already defined");
	    			return retrieveLoadedPackage(classLoader, name);
	    		}
			});
	    }
	    
		private void definePackageFor(Class<?> cls, 
			ClassLoader classLoader,
			MethodHandle definePackageMethod
		) {
			if (cls.getName().contains(".")) {
				String pckgName = cls.getName().substring(
			    	0, cls.getName().lastIndexOf(".")
			    );
			    if (retrieveLoadedPackage(classLoader, pckgName) == null) {
			    	Synchronizer.execute(classLoader + "_" + pckgName,() -> {
			    		if (retrieveLoadedPackage(classLoader, pckgName) == null) {
			    			definePackage(classLoader, definePackageMethod, pckgName, null, null, null, null, null, null, null);
			    		}
			    	});
				}	
			}
		}
		
		public <T> Class<T> retrieveLoadedClass(ClassLoader classLoader, String className) {
			Collection<Class<?>> definedClasses = retrieveLoadedClasses(classLoader);
			synchronized(definedClasses) {
				Iterator<?> itr = definedClasses.iterator();
				while(itr.hasNext()) {
					Class<?> cls = (Class<?>)itr.next();
					if (cls.getName().equals(className)) {
						return (Class<T>) cls;
					}
				}
			}
			if (classLoader.getParent() != null) {
				return retrieveLoadedClass(classLoader.getParent(), className);
			}
			return null;
		}	
		
		public Set<Class<?>> retrieveLoadedClassesForPackage(ClassLoader classLoader, Predicate<Package> packagePredicate) {
			Set<Class<?>> classesFound = new HashSet<>();
			Collection<Class<?>> definedClasses = retrieveLoadedClasses(classLoader);
			synchronized(definedClasses) {
				Iterator<?> itr = definedClasses.iterator();
				while(itr.hasNext()) {
					Class<?> cls = (Class<?>)itr.next();
					Package classPackage = cls.getPackage();
					if (packagePredicate.test(classPackage)) {
						classesFound.add(cls);
					}
				}
			}
			if (classLoader.getParent() != null) {
				classesFound.addAll(retrieveLoadedClassesForPackage(classLoader.getParent(), packagePredicate));
			}
			return classesFound;
		}
		
		public Package retrieveLoadedPackage(ClassLoader classLoader, String packageName) {
			Map<String, ?> packages = retrieveLoadedPackages(classLoader);
			Object packageToFind = packages.get(packageName);
			if (packageToFind != null) {
				return retrieveLoadedPackage(classLoader, packageToFind, packageName);
			} else if (classLoader.getParent() != null) {
				return retrieveLoadedPackage(classLoader.getParent(), packageName);
			} else {
				return null;
			}
		}
		
		public ClassLoader getClassLoaderOfPath(ClassLoader classLoader, String path) {
			FileSystemItem fIS = FileSystemItem.ofPath(path);
			ClassLoader pathLoader = null;
			for (ClassLoader cl : getHierarchy(classLoader)) {
				URL[] urls = getURLs(cl);
				if (urls != null) {
					for (URL url : urls) {
						FileSystemItem loadedPathFIS = FileSystemItem.of(url);
						if (loadedPathFIS.equals(fIS) || loadedPathFIS.isParentOf(fIS)) {
							pathLoader = cl;
						}
					}
				}
			}
			return pathLoader;
		}
		
		public boolean isItPossibleToAddClassPaths(ClassLoader classLoader) {
			if (classLoader != null) {
				if (classLoader instanceof URLClassLoader || isBuiltinClassLoader(classLoader) || classLoader instanceof PathScannerClassLoader) {
					return true;
				} else {
					return isItPossibleToAddClassPaths(getParent(classLoader));
				}
			} else {
				return false;
			}
		}
		
		public Collection<String> addClassPath(ClassLoader classLoader, String... classPaths) {
			return addClassPaths(classLoader, Arrays.asList(classPaths));
		}
		
		public Collection<String> addClassPath(ClassLoader classLoader, Predicate<String> checkForAddedClasses, String... classPaths) {
			return addClassPaths(classLoader, checkForAddedClasses, Arrays.asList(classPaths));
		}
		
		public Collection<String> addClassPaths(ClassLoader classLoader, Predicate<String> checkForAddedClasses, Collection<String>... classPathCollections) {
			if (LowLevelObjectsHandler.isClassLoaderDelegate(classLoader)) {
				return addClassPaths(Fields.getDirect(classLoader, "classLoader"));
			}
			Collection<String> paths = new HashSet<>();
			for (Collection<String> classPaths : classPathCollections) {
				paths.addAll(classPaths);
			}
			if (classLoader instanceof URLClassLoader || LowLevelObjectsHandler.isBuiltinClassLoader(classLoader)) {	
				paths.removeAll(getAllLoadedPaths(classLoader));
				if (!paths.isEmpty()) {
					Object target = classLoader instanceof URLClassLoader ?
						classLoader :
						Fields.getDirect(classLoader, "ucp");
					if (target != null) {
						Consumer<URL> classPathAdder = 	urls -> Methods.invokeDirect(target, "addURL", urls);
						paths.stream().map(classPath -> FileSystemItem.ofPath(classPath).getURL()).forEach(url -> {
							classPathAdder.accept(url);
						});
						return paths;
					}
				}
			} else if (classLoader instanceof PathScannerClassLoader) {
				return ((PathScannerClassLoader)classLoader).scanPathsAndAddAllByteCodesFound(paths, checkForAddedClasses);
			}
			return new HashSet<>();
		}
		
		public Collection<String> addClassPaths(ClassLoader classLoader, Collection<String>... classPathCollections) {
			return addClassPaths(classLoader, (path) -> false, classPathCollections);
		}

		public Collection<String> getAllLoadedPaths(ClassLoader classLoader) {
			return getAllLoadedPaths(classLoader, true);
		}
		
		public Collection<String> getAllLoadedPaths(ClassLoader classLoader, boolean considerThePathsLoadedByURLClassLoaderPathsAndBuiltinClassLoaderAsLoadeded) {
			Collection<String> allLoadedPaths = new LinkedHashSet<>();
			while((classLoader = getParent(classLoader)) != null) {
				if (classLoader instanceof PathScannerClassLoader) {
					allLoadedPaths.addAll(((PathScannerClassLoader)classLoader).loadedPaths);
				} else if (considerThePathsLoadedByURLClassLoaderPathsAndBuiltinClassLoaderAsLoadeded) {
					URL[] resUrl = getURLs(classLoader);
					if (resUrl != null) {
						for (int i = 0; i < resUrl.length; i++) {
							allLoadedPaths.add(Paths.convertURLPathToAbsolutePath(resUrl[i].getPath()));
						}
					}
				}
			}
			return allLoadedPaths;
		}
		
		public boolean isBuiltinClassLoader(ClassLoader classLoader) {
			return LowLevelObjectsHandler.isBuiltinClassLoader(classLoader);
		}
		
		public URL[] getURLs(ClassLoader classLoader) {
			if (classLoader instanceof URLClassLoader) {
				return ((URLClassLoader)classLoader).getURLs();
			} else if (LowLevelObjectsHandler.isClassLoaderDelegate(classLoader)) {
				return getURLs(Fields.getDirect(classLoader, "classLoader"));
			} else if (LowLevelObjectsHandler.isBuiltinClassLoader(classLoader)) {
				Object urlClassPath = Fields.getDirect(classLoader, "ucp");
				if (urlClassPath != null) {
					return Methods.invoke(urlClassPath, "getURLs");
				}
			} else if (classLoader instanceof PathScannerClassLoader) {
				return ((PathScannerClassLoader)classLoader).getURLs();
			}
			return null;
		}
		
		public void unregister(ClassLoader classLoader) {
			classLoadersClasses.remove(classLoader);
			classLoadersPackages.remove(classLoader);
		}
		
		@Override
		public void close() {
			if (this != StaticComponentContainer.ClassLoaders) {
				this.classLoadersClasses.clear();
				this.classLoadersClasses = null;
				this.classLoadersMethods.clear();
				this.classLoadersMethods = null;
				this.classLoadersPackages.clear();
				this.classLoadersPackages = null;
			} else {
				Throwables.throwException("Could not close singleton instance {}", this);
			}
		}
	}

}
