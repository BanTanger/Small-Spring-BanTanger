package com.bantanger.springframework.beans;

import com.bantanger.springframework.beans.factory.config.BeanDefinition;
import com.bantanger.springframework.beans.factory.config.BeanReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean 属性信息集合
 * @author BanTanger 半糖
 * @Date 2023/2/7 11:27
 */
public class PropertyValues {

    private final List<PropertyValue> propertyValueList = new ArrayList<>();

    public void addPropertyValue(PropertyValue pv) {
        this.propertyValueList.add(pv);
    }

    public PropertyValue[] getPropertyValues() {
        return this.propertyValueList.toArray(new PropertyValue[0]);
    }

    public PropertyValue getPropertyValue(String propertyName) {
        for (PropertyValue pv : this.propertyValueList) {
            if (pv.getName().equals(propertyName)) {
                return pv;
            }
        }
        return null;
    }

}
