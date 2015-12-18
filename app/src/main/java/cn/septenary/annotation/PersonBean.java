package cn.septenary.annotation;

import cn.septenary.MyAnnotation;

@MyAnnotation
public class PersonBean {

    public String name;
    public String address;

    public PersonBean(String name, String address) {
        this.name = name;
        this.address = address;
    }

    @Override
    public String toString() {
        return StringUtil.createString(this);
    }
}