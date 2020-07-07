package org.burningwave.core.examples.member;

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;

import org.burningwave.core.classes.MemoryClassLoader;


@SuppressWarnings("unused")
public class ConstructorsHandler {
    
    public static void execute() {
        //Inoking constructor by using reflection
        MemoryClassLoader classLoader = Constructors.newInstanceOf(MemoryClassLoader.class, Thread.currentThread().getContextClassLoader());
        
        //Inoking constructor with a null parameter value by using MethodHandle
        classLoader = Constructors.newInstanceDirectOf(MemoryClassLoader.class, new Object[] {null});
        System.out.println(classLoader);
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
