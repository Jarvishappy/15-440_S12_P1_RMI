package rmi.service.server.impl;

import rmi.RMIException;
import rmi.service.server.FileServer;

import java.io.FileNotFoundException;

/**
 * Remote interface implementation, this class should stay on the server.
 * @date 2014-10-18 2:50 PM
 */
public class FileServerImpl implements FileServer {

    @Override
    public long size(String path) throws FileNotFoundException, RMIException {
        return 0;
    }

    @Override
    public byte[] retrive(String path) throws FileNotFoundException, RMIException {
        return new byte[0];
    }
}
