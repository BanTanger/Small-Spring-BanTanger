package com.bantanger.springframework.beans.factory.support.instantiate;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.bantanger.springframework.beans.exception.BeansException;
import com.bantanger.springframework.beans.factory.aware.Aware;
import com.bantanger.springframework.beans.factory.aware.BeanClassLoaderAware;
import com.bantanger.springframework.beans.factory.aware.BeanFactoryAware;
import com.bantanger.springframework.beans.factory.aware.BeanNameAware;
import com.bantanger.springframework.beans.factory.config.AutowireCapableBeanFactory;
import com.bantanger.springframework.beans.factory.config.definition.PropertyValue;
import com.bantanger.springframework.beans.factory.config.definition.PropertyValues;
import com.bantanger.springframework.beans.factory.config.definition.BeanDefinition;
import com.bantanger.springframework.beans.factory.config.definition.BeanReference;
import com.bantanger.springframework.beans.factory.config.processor.BeanPostProcessor;
import com.bantanger.springframework.beans.factory.support.manage.DisposableBean;
import com.bantanger.springframework.beans.factory.support.manage.DisposableBeanAdapter;
import com.bantanger.springframework.beans.factory.support.manage.InitializingBean;
import com.bantanger.springframework.beans.factory.support.strategy.CglibSubclassingInstantiationStrategy;
import com.bantanger.springframework.beans.factory.support.strategy.InstantiationStrategy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 抽象类 AbstractAutowireCapableBeanFactory
 * 实例化 Bean 类
 *
 * @author BanTanger 半糖
 * @Date 2023/2/6 19:54
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory implements AutowireCapableBeanFactory {

    private InstantiationStrategy instantiationStrategy = new CglibSubclassingInstantiationStrategy();

    @Override
    protected Object createBean(String beanName, BeanDefinition beanDefinition, Object[] args) throws BeansException {
        Object bean = null;
        try {
            // 从 BeanDefinition 获取 Bean 的类信息，实例化完整的类: 有参无参均可
            bean = createBeanInstance(beanDefinition, beanName, args);
            // 给 Bean 填充属性
            applyPropertyValues(beanName, bean, beanDefinition);
            // 执行 Bean 的初始化方法和 BeanPostProcessor 的前置和后置处理方法
            bean = initializeBean(beanName, bean, beanDefinition);
        } catch (Exception e) {
            throw new BeansException("Instantiation of bean failed -- 实例化容器失败", e);
        }

        // 注册已经实现了 DisposableBean 接口的 Bean 对象
        registerDisposableBeanIfNecessary(beanName, bean, beanDefinition);

        // 创建修改 Bean 之后判断 bean 到底是单例还是原型模式
        if (beanDefinition.isSingleton()) {
            addSingleton(beanName, bean);
        }
        return bean;
    }

    protected Object createBeanInstance(BeanDefinition beanDefinition, String beanName, Object[] args) {
        Constructor constructorToUse = null;
        Class<?> beanClass = beanDefinition.getBeanClass();
        // 获取 bean 对象构造器列表
        Constructor<?>[] declaredConstructors = beanClass.getDeclaredConstructors();
        for (Constructor<?> ctor : declaredConstructors) {
            if (null != args && ctor.getParameterTypes().length == args.length) {
                constructorToUse = ctor;
                break;
            }
        }
        return getInstantiationStrategy().instantiate(beanDefinition, beanName, constructorToUse, args);
    }

    /**
     * Bean 属性填充
     *
     * @param beanName       bean对象名称
     * @param bean           bean对象
     * @param beanDefinition bean对象定义
     */
    protected void applyPropertyValues(String beanName, Object bean, BeanDefinition beanDefinition) {
        try {
            PropertyValues propertyValues = beanDefinition.getPropertyValues();
            for (PropertyValue propertyValue : propertyValues.getPropertyValues()) {

                String name = propertyValue.getName();
                Object value = propertyValue.getValue();

                if (value instanceof BeanReference) {
                    // 如果遇到的是 BeanReference 类型的 value，说明是非基本类型，需要递归获取 Bean 实例，调用 getBean 方法
                    // A 依赖 B，先获取 B 的对象实例
                    BeanReference beanReference = (BeanReference) value;
                    // 递归获取 Bean 实例
                    value = getBean(beanReference.getBeanName());
                }
                // TODO 属性填充，并没有处理循环依赖的问题
                BeanUtil.setFieldValue(bean, name, value);
            }
        } catch (Exception e) {
            throw new BeansException("Error setting property values：" + beanName
                    + " -- " + beanName + " 的属性值设置错误");
        }
    }

    protected InstantiationStrategy getInstantiationStrategy() {
        return instantiationStrategy;
    }

    public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
        this.instantiationStrategy = instantiationStrategy;
    }

    private Object initializeBean(String beanName, Object bean, BeanDefinition beanDefinition) {
        if (bean instanceof Aware) {
            if (bean instanceof BeanFactoryAware) {
                ((BeanFactoryAware) bean).setBeanFactory(this);
            }
            if (bean instanceof BeanClassLoaderAware) {
                ((BeanClassLoaderAware) bean).setBeanClassLoader(getBeanClassLoader());
            }
            if (bean instanceof BeanNameAware) {
                ((BeanNameAware) bean).setBeanName(beanName);
            }
        }

        // 1. 执行 BeanPostProcessor Before 处理
        Object wrappedBean = applyBeanPostProcessorBeforeInitialization(bean, beanName);

        // 执行 Bean 对象的初始化方法
        try {
            invokeInitMethods(beanName, wrappedBean, beanDefinition);
        } catch (Exception e) {
            throw new BeansException("Invocation of init method of bean[" + beanName + "] failed", e);
        }

        // 2. 执行 BeanPostProcessor After 处理
        wrappedBean = applyBeanPostProcessorAfterInitialization(bean, beanName);
        return wrappedBean;
    }

    private void invokeInitMethods(String beanName, Object bean, BeanDefinition beanDefinition) throws Exception {
        // 1. 调用初始化方法
        if (bean instanceof InitializingBean) {
            ((InitializingBean) bean).afterPropertiesSet();
        }

        // 2. 注解配置的 init-method,{再做判断，避免二次初始化}
        String initMethodName = beanDefinition.getInitMethodName();
        if (StrUtil.isNotEmpty(initMethodName) && !(bean instanceof InitializingBean)) {
            Method initMethod = beanDefinition.getBeanClass().getMethod(initMethodName);
            if (null == initMethod) {
                throw new BeansException("Could not find an init method named '" + initMethodName + "' on bean with name '" + beanName + "'");
            }
            initMethod.invoke(bean);
        }
    }

    protected void registerDisposableBeanIfNecessary(String beanName, Object bean, BeanDefinition beanDefinition) {
        // 非 Singleton 类型的 Bean 不执行销毁方法
        if (!beanDefinition.isSingleton()) {
            return ;
        }

        if (bean instanceof DisposableBean || StrUtil.isNotEmpty(beanDefinition.getDestroyMethodName())) {
            registerDisposableBean(beanName, new DisposableBeanAdapter(bean, beanName, beanDefinition));
        }
    }

    @Override
    public Object applyBeanPostProcessorBeforeInitialization(Object existingBean, String beanName) throws BeansException {
        Object result = existingBean;
        for (BeanPostProcessor processor : getBeanPostProcessors()) {
            Object current = processor.postProcessBeforeInitialization(result, beanName);
            if (null == current) {
                return result;
            }
            result = current;
        }
        return result;
    }

    @Override
    public Object applyBeanPostProcessorAfterInitialization(Object existingBean, String beanName) throws BeansException {
        Object result = existingBean;
        for (BeanPostProcessor processor : getBeanPostProcessors()) {
            Object current = processor.postProcessAfterInitialization(result, beanName);
            if (null == current) {
                return result;
            }
            result = current;
        }
        return result;
    }

}
