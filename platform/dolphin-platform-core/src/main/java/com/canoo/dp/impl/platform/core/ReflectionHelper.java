/*
 * Copyright 2015-2017 Canoo Engineering AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.canoo.dp.impl.platform.core;

import com.canoo.platform.core.DolphinRuntimeException;
import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.apiguardian.api.API.Status.INTERNAL;

@API(since = "0.x", status = INTERNAL)
public class ReflectionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionHelper.class);


    private ReflectionHelper() {
    }

    public static <T> T getPrivileged(final Field field, final Object bean) {
        Assert.requireNonNull(field, "field");
        return (T) AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                boolean wasAccessible = field.isAccessible();
                try {
                    field.setAccessible(true);
                    return field.get(bean);
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    throw new IllegalStateException("Cannot set field: "
                            + field, ex);
                } finally {
                    field.setAccessible(wasAccessible);
                }
            }
        });
    }

    public static void setPrivileged(final Field field, final Object bean,
                                     final Object value) {
        Assert.requireNonNull(field, "field");
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                boolean wasAccessible = field.isAccessible();
                try {
                    field.setAccessible(true);
                    field.set(bean, value);
                    return null; // return nothing...
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    throw new IllegalStateException("Cannot set field: "
                            + field, ex);
                } finally {
                    field.setAccessible(wasAccessible);
                }
            }
        });
    }

    public static <T> T invokePrivileged(final Method method, final Object instance, final Object... args) {
        Assert.requireNonNull(method, "method");
        Assert.requireNonNull(instance, "instance");
        return AccessController.doPrivileged((PrivilegedAction<T>) new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                boolean wasAccessible = method.isAccessible();
                try {
                    method.setAccessible(true);
                    return method.invoke(instance, args);
                } catch (InvocationTargetException ex) {
                    throw new DolphinRuntimeException("Error while calling method '"
                            + method.getName() + "' on instance of type '" + instance.getClass() + "'. Method details: " + method.toGenericString(), ex);
                } catch (IllegalAccessException ex) {
                    throw new DolphinRuntimeException("Cannot invoke method '"
                            + method.getName() + "' on instance of type '" + instance.getClass() + "'. Method details: " + method.toGenericString(), ex);
                } finally {
                    method.setAccessible(wasAccessible);
                }
            }
        });
    }

    public static Field getInheritedDeclaredField(final Class<?> type, final String name) {
        Assert.requireNonNull(type, "type");
        Assert.requireNonNull(name, "name");

        Class<?> i = type;
        while (i != null && i != Object.class) {
            for (Field field : Arrays.asList(i.getDeclaredFields())) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
        }
        return null;
    }

    public static List<Field> getInheritedDeclaredFields(final Class<?> type) {
        Assert.requireNonNull(type, "type");
        List<Field> result = new ArrayList<>();
        Class<?> i = type;
        while (i != null && i != Object.class) {
            result.addAll(Arrays.asList(i.getDeclaredFields()));
            i = i.getSuperclass();
        }
        return result;
    }

    public static List<Method> getInheritedDeclaredMethods(final Class<?> type) {
        Assert.requireNonNull(type, "type");
        List<Method> result = new ArrayList<>();
        Class<?> i = type;
        while (i != null && i != Object.class) {
            result.addAll(Arrays.asList(i.getDeclaredMethods()));
            i = i.getSuperclass();
        }
        return result;
    }

    public static Optional<Method> getMethod(final Class<?> type, final String name, Class... paramTypes) {
        return getInheritedDeclaredMethods(type).stream()
                .filter(m -> m.getName().equals(name))
                .filter(m -> Arrays.equals(m.getParameterTypes(), paramTypes)).findFirst();
    }


    public static boolean isProxyInstance(final Object bean) {
        Assert.requireNonNull(bean, "bean");
        return Proxy.isProxyClass(bean.getClass());
    }

    public static Class getTypeParameter(final Field field) {
        Assert.requireNonNull(field, "field");
        try {
            ParameterizedType pType = (ParameterizedType) field.getGenericType();
            if (pType.getActualTypeArguments().length > 0) {
                return (Class) pType.getActualTypeArguments()[0];
            }
        } catch (ClassCastException ex) {
            LOG.warn("can not extract parameterized type for field: " +field.getName() + ", bean: "+ field.getDeclaringClass().getName());
        }
        return null;
    }

    public static boolean isNumber(final Class<?> cls) {
        Assert.requireNonNull(cls, "cls");
        return (Number.class.isAssignableFrom(cls) || isPrimitiveNumber(cls));
    }

    public static boolean isPrimitiveNumber(final Class<?> cls) {
        Assert.requireNonNull(cls, "cls");
        return (Integer.TYPE.equals(cls) || Long.TYPE.equals(cls) || Double.TYPE.equals(cls) || Float.TYPE.equals(cls) || Short.TYPE.equals(cls) || Byte.TYPE.equals(cls));
    }

    public static boolean hasGenericTypeCount(final ParameterizedType type, final int count) {
        Assert.requireNonNull(type, "type");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        return type.getActualTypeArguments().length == count;
    }

    public static Type getGenericType(final ParameterizedType type, final int index) {
        Assert.requireNonNull(type, "type");
        if (index < 0) {
            throw new IllegalArgumentException("Can not get generic type at negativ index.");
        }
        if (type.getActualTypeArguments().length <= index) {
            throw new IllegalArgumentException("Can not get generic type at index " + index + " since type has only " + type.getActualTypeArguments().length + " generic parameters. Type: " + type);
        }
        return type.getActualTypeArguments()[0];
    }

    public static ParameterizedType toParameterizedType(final Type type) {
        Assert.requireNonNull(type, "type");
        if (isParameterizedType(type)) {
            return (ParameterizedType) type;
        } else {
            throw new IllegalArgumentException("The given type is not a ParameterizedType. Type: " + type);
        }
    }

    public static boolean isParameterizedType(Type type) {
        Assert.requireNonNull(type, "type");
        if (type instanceof ParameterizedType) {
            return true;
        } else {
            return false;
        }
    }

    public static Class toClass(final Type type) {
        if (isClass(type)) {
            return (Class) type;
        } else {
            throw new IllegalArgumentException("The given type is not a class. Type: " + type);
        }
    }

    public static boolean isClass(final Type type) {
        Assert.requireNonNull(type, "type");
        if (type instanceof Class) {
            return true;
        } else {
            return false;
        }
    }
}
