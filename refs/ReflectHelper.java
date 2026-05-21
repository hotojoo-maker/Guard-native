package com.catfish.newvip.util;

import android.text.TextUtils;
import com.catfish.newvip.MainEntry;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/* loaded from: C:\Users\Me\Desktop\apk\dex_extract\classes17.dex */
public class ReflectHelper {
    public static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            MLOG.e(e);
            return null;
        }
    }

    public static Method getMethod(Class paramClass, String paramString, Class[] paramArrayOfClass) {
        if (paramClass == null || paramString == null) {
            return null;
        }
        Method result = null;
        Class localCls = paramClass;
        while (result == null && localCls != null && localCls != Object.class) {
            if (paramArrayOfClass == null) {
                try {
                    result = localCls.getDeclaredMethod(paramString, new Class[0]);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                result = localCls.getDeclaredMethod(paramString, paramArrayOfClass);
            }
            if (result == null) {
                if (paramString == "k") {
                    localCls = localCls.getSuperclass();
                    MLOG.e("getMethod -- " + localCls.getName());
                } else {
                    localCls = localCls.getSuperclass();
                }
            }
        }
        return result;
    }

    public static Method getPubicMethod(Class paramClass, String paramString, Class[] paramArrayOfClass) {
        if (paramClass == null || paramString == null) {
            return null;
        }
        try {
            Method result = paramClass.getMethod(paramString, paramArrayOfClass);
            return result;
        } catch (Exception ex) {
            MLOG.e(ex.toString());
            return null;
        }
    }

    public static <T> T convertObject(Object obj, String str) {
        try {
            return (T) Class.forName(str).cast(obj);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Field getField(Class paramClass, String paramString) {
        if (paramClass == null || TextUtils.isEmpty(paramString)) {
            return null;
        }
        Field result = null;
        Class localCls = paramClass;
        while (result == null && localCls != null && localCls != Object.class) {
            try {
                Field[] arrayOfField = localCls.getDeclaredFields();
                for (int idx = 0; idx < arrayOfField.length; idx++) {
                    result = arrayOfField[idx];
                    boolean flag = TextUtils.equals(result.getName(), paramString);
                    if (flag) {
                        break;
                    }
                    result = null;
                }
            } catch (Exception ex) {
                MLOG.e(localCls.getName() + "   " + ex.toString());
            }
            if (result == null) {
                localCls = localCls.getSuperclass();
            }
        }
        return result;
    }

    public static Object getFieldValueByFieldName(Object owner, String fieldName) {
        try {
            Class cls = owner.getClass();
            Field field = getField(cls, fieldName);
            if (field == null) {
                MLOG.e(cls.getName() + "[getFieldValueByFieldName]not found field -" + fieldName);
                return null;
            }
            field.setAccessible(true);
            return field.get(owner);
        } catch (Exception e) {
            MLOG.e(e.getMessage());
            return null;
        }
    }

    public static Object setFieldValueByFieldName(Object object, String fieldName, Object value) {
        Class cls;
        Field f;
        try {
            cls = object.getClass();
            f = getField(cls, fieldName);
        } catch (Exception e) {
            MLOG.e(e.getMessage());
        }
        if (f == null) {
            MLOG.e(cls.getName() + " [setFieldValueByFieldName]not found field -" + fieldName);
            return object;
        }
        f.setAccessible(true);
        f.set(object, value);
        return object;
    }

    public static Object callMethod(Object owner, String methodName) {
        return callMethod(owner, methodName, null, null);
    }

    public static Object callMethod(Object owner, String methodName, Object[] args) {
        return callMethod(owner, methodName, args, null);
    }

    public static Object callMethod(Object owner, String methodName, Object[] args, Class[] argTypes) {
        if (owner == null) {
            MLOG.e("owner is null");
            return null;
        }
        Class<?> ownerClass = owner.getClass();
        if (argTypes == null && args != null && args.length > 0) {
            argTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i].getClass();
            }
        }
        try {
            Method method = findPublicMethod(ownerClass, methodName, argTypes);
            if (method == null) {
                method = findMethodDeep(ownerClass, methodName, argTypes);
            }
            if (method == null) {
                logMethodNotFound(ownerClass, methodName, argTypes);
                return null;
            }
            method.setAccessible(true);
            Object[] processedArgs = processArguments(method, args);
            Object result = method.invoke(owner, processedArgs);
            MLOG.i(ownerClass.getSimpleName() + "." + methodName + "() called successfully");
            return result;
        } catch (InvocationTargetException e) {
            MLOG.e("Method " + methodName + " threw exception: " + e.getTargetException());
            e.printStackTrace();
            return null;
        } catch (Exception e2) {
            MLOG.e("Failed to call method " + methodName + ": " + e2.getMessage());
            e2.printStackTrace();
            return null;
        }
    }

    private static Method findPublicMethod(Class<?> clazz, String methodName, Class<?>[] argTypes) {
        try {
            return clazz.getMethod(methodName, argTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findMethodDeep(Class<?> clazz, String methodName, Class<?>[] argTypes) {
        if (clazz == null || clazz == Object.class) {
            return null;
        }
        try {
            return clazz.getDeclaredMethod(methodName, argTypes);
        } catch (NoSuchMethodException e) {
            Method method = findMethodDeep(clazz.getSuperclass(), methodName, argTypes);
            if (method != null) {
                return method;
            }
            for (Class<?> interfaceClass : clazz.getInterfaces()) {
                Method method2 = findMethodDeep(interfaceClass, methodName, argTypes);
                if (method2 != null) {
                    return method2;
                }
            }
            return null;
        }
    }

    private static Object[] processArguments(Method method, Object[] args) {
        if (args == null) {
            return null;
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != args.length) {
            return args;
        }
        Object[] processed = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            processed[i] = convertArgument(args[i], paramTypes[i]);
        }
        return processed;
    }

    private static Object convertArgument(Object arg, Class<?> targetType) {
        if (arg == null) {
            return null;
        }
        Class<?> sourceType = arg.getClass();
        if (targetType.isAssignableFrom(sourceType)) {
            return arg;
        }
        if (targetType == Integer.TYPE) {
            return Integer.valueOf(((Number) arg).intValue());
        }
        if (targetType == Integer.class && sourceType == Integer.TYPE) {
            return arg;
        }
        if (targetType == Boolean.TYPE) {
            return arg;
        }
        if (targetType == Long.TYPE) {
            return Long.valueOf(((Number) arg).longValue());
        }
        if (targetType == Double.TYPE) {
            return Double.valueOf(((Number) arg).doubleValue());
        }
        return arg;
    }

    private static void logMethodNotFound(Class<?> clazz, String methodName, Class<?>[] argTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Method not found: ").append(clazz.getName()).append(".").append(methodName).append("(");
        if (argTypes != null) {
            for (int i = 0; i < argTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(argTypes[i] != null ? argTypes[i].getSimpleName() : "null");
            }
        }
        sb.append(")");
        MLOG.e(sb.toString());
    }

    public static Object callMethod2(Object owner, String methodName, Object[] args, Class[] argTypes) {
        Class ownerClass = owner.getClass();
        if (argTypes == null && args != null && args.length > 0) {
            argTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i].getClass();
            }
        }
        try {
            Method method = getMethod(ownerClass, methodName, argTypes);
            if (method == null) {
                String tmpInfo = "";
                if (argTypes != null) {
                    for (int i2 = 0; i2 < argTypes.length; i2++) {
                        tmpInfo = tmpInfo + " " + args[i2].getClass().getName();
                    }
                }
                MLOG.e(ownerClass.getName() + " not found method-" + methodName + "  args:" + tmpInfo);
                return null;
            }
            method.setAccessible(true);
            Object result = method.invoke(owner, args);
            MLOG.i(ownerClass.getName() + "  call method-" + methodName);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            MLOG.e(ownerClass.getName() + " call method-" + methodName + " error:" + e.getMessage());
            return null;
        }
    }

    public static Object callStaticMethod(Class ownerClass, String methodName, Object[] args) {
        return callStaticMethod(ownerClass, methodName, args, null);
    }

    public static Object callStaticMethod(Class ownerClass, String methodName, Object[] args, Class[] argTypes) {
        String tmpInfo = "";
        if (argTypes == null && args != null) {
            argTypes = new Class[args.length];
            int j = args.length;
            for (int i = 0; i < j; i++) {
                argTypes[i] = args[i].getClass();
                tmpInfo = tmpInfo + " " + args[i].getClass().getName();
            }
        }
        try {
            Method method = getMethod(ownerClass, methodName, argTypes);
            if (method == null) {
                MainEntry.printCall();
                MLOG.e(ownerClass.getName() + " not found static method-" + methodName + "  args:" + tmpInfo);
                return null;
            }
            method.setAccessible(true);
            Object result = method.invoke(null, args);
            return result;
        } catch (Exception e) {
            MLOG.e(ownerClass.getName() + " call static method-" + methodName + " error:" + e.getMessage());
            return null;
        }
    }

    public static Object invokeMethod(Method method, Object obj, Object[] args) {
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            Object result = method.invoke(obj, args);
            return result;
        } catch (Exception e) {
            MLOG.e(e.getMessage());
            return null;
        }
    }

    public static void invokeVoidMethod(Method method, Object obj, Object[] args) {
        if (method == null) {
            return;
        }
        try {
            method.setAccessible(true);
            method.invoke(obj, args);
        } catch (Exception e) {
            MLOG.e(e.getMessage());
        }
    }

    public static boolean isInstanceOf(String className, Object obj) {
        try {
            Class<?> clazz = Class.forName(className);
            return clazz.isInstance(obj);
        } catch (ClassNotFoundException e) {
            MLOG.e("类不存在: " + className);
            return false;
        }
    }

    public static void reflectFieldInfo(String desc, Object model) {
        MLOG.i(desc);
        Field[] field = model.getClass().getDeclaredFields();
        String[] modelName = new String[field.length];
        String[] modelType = new String[field.length];
        MLOG.i(model.getClass().getName());
        for (int i = 0; i < field.length; i++) {
            String name = field[i].getName();
            modelName[i] = name;
            String type = field[i].getGenericType().toString();
            modelType[i] = type;
            field[i].setAccessible(true);
            try {
                MLOG.i("        field--" + name + "  type-" + type + "  value-" + field[i].get(model));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static void reflectMethodInfo(Object model) {
        Class object = model.getClass();
        Method[] methods = object.getDeclaredMethods();
        for (Method one : methods) {
            String info = object.getName();
            String info2 = info + "  " + one.getName() + "--" + one.getParameterCount() + "(";
            Class[] paramTypes = one.getParameterTypes();
            Parameter[] parameters = one.getParameters();
            for (int i = 0; i < one.getParameterCount(); i++) {
                info2 = info2 + paramTypes[i].getName() + " " + parameters[i].getName() + ",";
            }
            MLOG.i(info2 + ") " + one.getReturnType());
        }
    }
}
