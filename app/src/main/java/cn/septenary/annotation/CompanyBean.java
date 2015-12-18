package cn.septenary.annotation;

import cn.septenary.MyAnnotation;

@MyAnnotation
public class CompanyBean {

    public String name;
    public String address;

    public CompanyBean(String name, String address) {
        this.name = name;
        this.address = address;
    }

    @Override
    public String toString() {
        return StringUtil.createString(this);
    }
}