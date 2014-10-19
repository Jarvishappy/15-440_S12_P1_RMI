package rmi.service.server;

import rmi.RMIException;

import java.io.FileNotFoundException;

/**
 * Remote interface
 * @date 2014-10-18 2:46 PM
 */
public interface FileServer {
    /**
     * Return file size on the server.
     * @param path file full path on the server
     * @return
     * @throws FileNotFoundException
     * @throws RMIException
     */
    public long size(String path) throws FileNotFoundException, RMIException;

    /**
     * Return file data on the server.
     * @param path file full path on the server
     * @return
     * @throws FileNotFoundException
     * @throws RMIException
     */
    public byte[] retrive(String path) throws FileNotFoundException, RMIException;
}
