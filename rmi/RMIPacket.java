package rmi;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 封装RMI网络传输的数据
 *
 * @author siyuan
 * @date 2015-04-12 7:47 PM
 */
public class RMIPacket implements Serializable {
    private String methodName;
    private Class<?>[] paramTypes;
    private Object[] args;

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParamTypes() {
        return paramTypes;
    }

    public void setParamTypes(Class<?>[] paramTypes) {
        this.paramTypes = paramTypes;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    @Override
    public String toString() {
        return "RMIPacket{" + "methodName:" + methodName + " paramTypes:" + Arrays.toString(paramTypes) + " args:"
                + Arrays.toString(args) + "}";
    }
}
