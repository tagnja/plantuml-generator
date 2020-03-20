package com.taogen.docs2uml.entity;

import lombok.Data;

/**
 * @author Taogen
 */
@Data
public class MyCommand {
    private String url;
    // TODO: update doc, pacakge -> pacakgeName
    private String packageName;
    private Boolean subPackage;

    public MyCommand() {
    }

    public MyCommand(String url){
        this.url = url;
    }

    public MyCommand(String url, String packageName) {
        this.url = url;
        this.packageName = packageName;
    }

    public MyCommand(String url, String packageName, Boolean subPackage) {
        this.url = url;
        this.packageName = packageName;
        this.subPackage = subPackage;
    }

    public String getPrefixUrl(){
        if (url != null){
            return url.substring(0, url.lastIndexOf('/') + 1);
        }
        return null;
    }
}
