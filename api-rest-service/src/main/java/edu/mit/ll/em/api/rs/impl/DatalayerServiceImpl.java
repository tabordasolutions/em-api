/**
 * Copyright (c) 2008-2018, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.em.api.rs.impl;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import edu.mit.ll.em.api.formatter.KmlFormatter;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.codehaus.jackson.map.ObjectMapper;
import org.forgerock.openam.utils.StringUtils;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.referencing.FactoryException;

import edu.mit.ll.em.api.dataaccess.ShapefileDAO;
import edu.mit.ll.em.api.rs.DatalayerDocumentServiceResponse;
import edu.mit.ll.em.api.rs.DatalayerService;
import edu.mit.ll.em.api.rs.DatalayerServiceResponse;
import edu.mit.ll.em.api.rs.FieldMapResponse;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.FileUtil;
import edu.mit.ll.em.api.util.SADisplayConstants;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.common.entity.UserOrg;
import edu.mit.ll.nics.common.entity.datalayer.Datalayer;
import edu.mit.ll.nics.common.entity.datalayer.Datalayerfolder;
import edu.mit.ll.nics.common.entity.datalayer.Datalayersource;
import edu.mit.ll.nics.common.entity.datalayer.Datasource;
import edu.mit.ll.nics.common.entity.datalayer.Document;
import edu.mit.ll.nics.common.entity.datalayer.Rootfolder;
import edu.mit.ll.nics.common.geoserver.api.GeoServer;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.DatalayerDAO;
import edu.mit.ll.nics.nicsdao.DocumentDAO;
import edu.mit.ll.nics.nicsdao.FolderDAO;
import edu.mit.ll.nics.nicsdao.UserDAO;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserSessionDAOImpl;
import org.springframework.util.CollectionUtils;

/**
 * 
 * @AUTHOR st23420
 *
 */
public class DatalayerServiceImpl implements DatalayerService {

	private static final Log logger = LogFactory.getLog(DatalayerServiceImpl.class);
	
	/** A standard KML root element. */
	private static final String KML_ROOT_START_TAG =
			"<kml xmlns=\"http://www.opengis.net/kml/2.2\" " +
			"xmlns:gx=\"http://www.google.com/kml/ext/2.2\" " +
			"xmlns:kml=\"http://www.opengis.net/kml/2.2\" " +
			"xmlns:atom=\"http://www.w3.org/2005/Atom\">";

	/** A pattern that matches KML documents without a root <kml> element. */
	private static final Pattern MALFORMED_KML_PATTERN = Pattern.compile("^\\s*<\\?xml[^>]+>\\s*<Document>", Pattern.MULTILINE);

    private Configuration emApiConfiguration;
	private DatalayerDAO datalayerDao;
	private FolderDAO folderDao;
	private DocumentDAO documentDao;
	private UserDAO userDao;
	private UserOrgDAOImpl userOrgDao;
	private UserSessionDAOImpl userSessionDao;
	
	private String fileUploadPath;
	private String mapServerURL;
	private String geoServerWorkspace;
	private String geoServerDatastore;
	private String webServerURL;

	private RabbitPubSubProducer rabbitProducer;

	private Client jerseyClient;

    public DatalayerServiceImpl(Configuration emApiConfiguration, DatalayerDAO datalayerDao, FolderDAO folderDao, DocumentDAO documentDao, UserDAO userDao, UserOrgDAOImpl userOrgDao, UserSessionDAOImpl userSessionDao, RabbitPubSubProducer rabbitProducer, Client jerseyClient) {
        this.emApiConfiguration = emApiConfiguration;
        this.datalayerDao = datalayerDao;
        this.folderDao = folderDao;
        this.documentDao = documentDao;
        this.userDao = userDao;
        this.userOrgDao = userOrgDao;
        this.userSessionDao = userSessionDao;
        this.rabbitProducer = rabbitProducer;
        this.jerseyClient = jerseyClient;
        this.initializeConfigProperties();
    }

	private void initializeConfigProperties() {
		fileUploadPath = emApiConfiguration.getString(APIConfig.FILE_UPLOAD_PATH, "/opt/data/nics/upload");
		geoServerWorkspace = emApiConfiguration.getString(APIConfig.IMPORT_SHAPEFILE_WORKSPACE, "nics");
		geoServerDatastore = emApiConfiguration.getString(APIConfig.IMPORT_SHAPEFILE_STORE, "shapefiles");
		mapServerURL = emApiConfiguration.getString(APIConfig.EXPORT_MAPSERVER_URL);
		webServerURL = emApiConfiguration.getString(APIConfig.EXPORT_WEBSERVER_URL);
	}

    public String getFileUploadPath() {
        return this.fileUploadPath;
    }

    public String getGeoServerWorkspace() {
        return this.geoServerWorkspace;
    }

    public String getGeoServerDatastore() {
        return this.geoServerDatastore;
    }

    public String getMapServerURL() {
        return this.mapServerURL;
    }

    public String getWebServerURL() {
        return this.webServerURL;
    }

	@Override
	public Response getTrackingLayers(int workspaceId) {
		FieldMapResponse response = new FieldMapResponse();
		try{
			List<Map<String,Object>> layers = datalayerDao.getTrackingLayers(workspaceId, true);
			layers.addAll(datalayerDao.getTrackingLayers(workspaceId, false));
			response.setData(layers);
		}catch(Exception e){
			logger.error("Failed to retrieve data layers", e);
			response.setMessage("Failed to retrieve data layers");
			return Response.ok(response).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return Response.ok(response).status(Status.OK).build();
	}

	@Override
	public Response getDatalayers(String folderId) {
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		try{
			datalayerResponse.setDatalayerfolders(datalayerDao.getDatalayerFolders(folderId));
		}catch(Exception e){
			logger.error("Failed to retrieve data layers", e);
			datalayerResponse.setMessage("Failed to retrieve data layers");
			return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}

	@Override
	public Response getDatasources(String type) {
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		try{
			datalayerResponse.setDatasources(datalayerDao.getDatasources(type));
		}catch(Exception e){
			logger.error("Failed to retrieve data sources", e);
			datalayerResponse.setMessage("Failed to retrieve data sources");
			return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}

	@Override
	public Response postDatasource(String type, Datasource source) {
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		
		try{
			int dataSourceTypeId = datalayerDao.getDatasourceTypeId(type);
			source.setDatasourcetypeid(dataSourceTypeId);
			
			String dataSourceId = datalayerDao.insertDataSource(source);
			Datasource newSource = datalayerDao.getDatasource(dataSourceId);
			datalayerResponse.setDatasources(Arrays.asList(newSource));
			datalayerResponse.setMessage("ok");
			response = Response.ok(datalayerResponse).status(Status.OK).build();
		} catch(Exception e) {
			logger.error("Failed to insert data source", e);
			datalayerResponse.setMessage("failed");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		return response;
	}

	@Override
	public Response postDataLayer(int workspaceId, String dataSourceId, Datalayer datalayer) {
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		Datalayerfolder newDatalayerFolder = null;
		
		try{
			datalayer.setCreated(new Date());
			datalayer.getDatalayersource().setCreated(new Date());
			datalayer.getDatalayersource().setDatasourceid(dataSourceId);
			
			String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);

			//Currently always uploads to Data
			Rootfolder folder = folderDao.getRootFolder("Data", workspaceId);
			int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folder.getFolderid());
				
			datalayerDao.insertDataLayerFolder(folder.getFolderid(), datalayerId, nextFolderIndex);
			newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folder.getFolderid());
			
			datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
			datalayerResponse.setMessage("ok");
			response = Response.ok(datalayerResponse).status(Status.OK).build();
		}
		catch(Exception e) {
			logger.error("Failed to insert data layer", e);
			datalayerResponse.setMessage("failed");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyNewChange(newDatalayerFolder, workspaceId);
			} catch (IOException e) {
				logger.error("Failed to publish DatalayerService message event", e);
			}
		}
		
		return response;
	}
	
	@Override
	public Response deleteDataLayer(int workspaceId, String dataSourceId){
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		boolean deleteDatalayer = false;
		
		try{
		
			deleteDatalayer = datalayerDao.removeDataLayer(dataSourceId);
		
			if(deleteDatalayer){
				datalayerResponse.setMessage("OK");
				response = Response.ok(datalayerResponse).status(Status.OK).build();	
			}
			else{
				datalayerResponse.setMessage("Failed to delete datalayer");
				response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
			
		
		}catch(Exception e){
			logger.error("Failed to delete data layer", e);
			datalayerResponse.setMessage("Failed to delete datalayer");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyDeleteChange(dataSourceId);
			} catch (IOException e) {
				logger.error("Failed to publish DatalayerService message event", e);
			}
		}
		
		return response;
	}
	
	public Response updateDataLayer(int workspaceId, Datalayer datalayer){
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		Datalayer dbDatalayer = null;
		
		try{
		
			dbDatalayer = datalayerDao.updateDataLayer(datalayer);
		
			if(dbDatalayer != null){
				datalayerResponse.setCount(1);
				datalayerResponse.setMessage("OK");
				response = Response.ok(datalayerResponse).status(Status.OK).build();	
			}
			else{
				datalayerResponse.setMessage("Failed to update datalayer");
				response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
			
		
		}catch(Exception e){
			logger.error("Failed to delete data layer", e);
			datalayerResponse.setMessage("Failed to delete datalayer");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyUpdateChange(dbDatalayer);
			} catch (IOException e) {
				logger.error("Failed to publish DatalayerService message event", e);
			}
		}
		
		datalayerResponse.setMessage("OK");
		response = Response.ok(datalayerResponse).status(Status.OK).build();	
		
		return response;
	}
	
	public Response postShapeDataLayer(int workspaceId, String displayName, MultipartBody body, String username) {
		if(!userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) &&
				!userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID) &&
				!userOrgDao.isUserRole(username, SADisplayConstants.GIS_ROLE_ID)){
			return getInvalidResponse();
		}
			
		ShapefileDAO geoserverDao = ShapefileDAO.getInstance();
		GeoServer geoserver = getGeoServer(APIConfig.getInstance().getConfiguration());
		String dataSourceId = getMapserverDatasourceId();
		if (dataSourceId == null) {
			throw new WebApplicationException("Failed to find configured NICS wms datasource");
		}
		
		Attachment aShape = body.getAttachment("shpFile");
		if (aShape == null) {
			throw new WebApplicationException("Required attachment 'shpFile' not found");
		}
		String shpFilename = aShape.getContentDisposition().getParameter("filename");
		String batchName = shpFilename.replace(".shp", "").replace(" ", "_");
		String layerName = batchName.concat(String.valueOf(System.currentTimeMillis()));
		
		//write all the uploaded files to the filesystem in a temp directory
		Path shapesDirectory = Paths.get(this.getFileUploadPath(), "shapefiles");
		Path batchDirectory = null;
		try {
			Files.createDirectories(shapesDirectory);
			
			batchDirectory = Files.createTempDirectory(shapesDirectory, batchName);
			List<Attachment> attachments = body.getAllAttachments();
			for(Attachment attachment : attachments) {
				String filename = attachment.getContentDisposition().getParameter("filename");
				String extension = FileUtil.getFileExtension(filename);
				if (extension != null) {
					Path path = batchDirectory.resolve(batchName.concat(extension));
					InputStream is = attachment.getDataHandler().getInputStream();
					Files.copy(is, path);
				}
			}
			
			//attempt to read our shapefile and accompanying files
			Path shpPath = batchDirectory.resolve(batchName.concat(".shp"));
			FileDataStore store = FileDataStoreFinder.getDataStore(shpPath.toFile());
			SimpleFeatureSource featureSource = store.getFeatureSource();
			
			//attempt to insert our features into their own table
			geoserverDao.insertFeatures(layerName, featureSource);
		} catch (IOException | FactoryException e) {
			try {
				geoserverDao.removeFeaturesTable(layerName);
			} catch (IOException ioe) { /* bury */}
			throw new WebApplicationException("Failed to import shapefile", e);
		} finally {
			//always clean up our temp directory
			if (batchDirectory != null) {
				try {
					FileUtil.deleteRecursively(batchDirectory);
				} catch (IOException e) {
					logger.error("Failed to cleanup shapefile batch directory", e);
				}
			}
		}
		
		//add postgis layer to map server
		if(!geoserver.addFeatureType(this.getGeoServerWorkspace(), this.getGeoServerDatastore(), layerName, "EPSG:3857")){
			try {
				geoserverDao.removeFeaturesTable(layerName);
			} catch (IOException e) { /* bury */}
			throw new WebApplicationException("Failed to create features " + layerName);
		}
		
		//apply styling default or custom sld
		String defaultStyleName = "defaultShapefileStyle";
		Attachment aSld = body.getAttachment("sldFile");
		if (aSld != null) {
			String sldXml = aSld.getObject(String.class);
			if (geoserver.addStyle(layerName, sldXml) ) {
				defaultStyleName = layerName;
			}
		}
		geoserver.updateLayerStyle(layerName, defaultStyleName);
		geoserver.updateLayerEnabled(layerName, true);

		//create datalayer and datalayersource for our new layer 
		int usersessionid = userSessionDao.getUserSessionid(username);
		
		Datalayer datalayer = new Datalayer(); 
		datalayer.setCreated(new Date());
		datalayer.setBaselayer(false);
		datalayer.setDisplayname(displayName);
		datalayer.setUsersessionid(usersessionid);
		
		Datalayersource dlsource = new Datalayersource();
		dlsource.setLayername(layerName);
		dlsource.setCreated(new Date());
		dlsource.setDatasourceid(dataSourceId);
		datalayer.setDatalayersource(dlsource);
		
		String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);
		Rootfolder folder = folderDao.getRootFolder("Data", workspaceId);
		int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folder.getFolderid());
		datalayerDao.insertDataLayerFolder(folder.getFolderid(), datalayerId, nextFolderIndex);

		//retrieve the new datalayerfolder to return to the client and broadcast
		Datalayerfolder newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folder.getFolderid());
		
		try {
			notifyNewChange(newDatalayerFolder, workspaceId);
		} catch (IOException e) {
			logger.error("Failed to publish DatalayerService message event", e);
		}
		
		DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
		datalayerResponse.setSuccess(true);
		datalayerResponse.setCount(1);
		datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}
	
	public Response postDataLayerDocument(int workspaceId, String fileExt, int userOrgId, String refreshRate, MultipartBody body, String username){
		int numericRefreshRate = parseRefreshRate(refreshRate);
		DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
		Response response = null;
		Datalayerfolder newDatalayerFolder = null;
		Datalayer datalayer = new Datalayer();
		datalayer.setDatalayersource(new Datalayersource());
		String dataSourceId = null;
		Document doc = null;
		Boolean uploadedDataLayer = false;
		String fileName = null;
		String filePath = null;
		Boolean valid = false;
		User user = null;
		
		try{
			
			user = userDao.getUser(username);
			Set<UserOrg> userOrgs = user.getUserorgs();
			Iterator<UserOrg> iter = userOrgs.iterator();
			
			while(iter.hasNext()){
				
				UserOrg userOrg = (UserOrg)iter.next();
				
				if(userOrg.getUserorgid() == userOrgId && 
						(userOrg.getSystemroleid() == SADisplayConstants.SUPER_ROLE_ID || 
						userOrg.getSystemroleid() == SADisplayConstants.GIS_ROLE_ID ||
						userOrg.getSystemroleid() == SADisplayConstants.ADMIN_ROLE_ID	)){
					valid = true;
				}
				
			}
			
			if(!valid){
				return getInvalidResponse();
			}
			
			for(Attachment attachment : body.getAllAttachments()) {
	
				if(MediaType.TEXT_PLAIN_TYPE.isCompatible(attachment.getContentType())){
					String attachmentName = attachment.getContentDisposition().getParameter("name").toString();
					if (attachmentName.equals("usersessionid")){
						datalayer.setUsersessionid(Integer.valueOf(attachment.getObject(String.class).toString()));
					} else if (attachmentName.equals("displayname")){
						datalayer.setDisplayname(attachment.getObject(String.class).toString());
					} else if (attachmentName.equals("baselayer")){
						datalayer.setBaselayer(Boolean.parseBoolean(attachment.getObject(String.class).toString()));
					}
				}
				else{
					String attachmentFilename = attachment.getContentDisposition().getParameter("filename").toLowerCase();
					if (attachmentFilename.toLowerCase().endsWith(".kmz")){
						filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.KMZ_TMP_UPLOAD_PATH,"/opt/data/nics/tmp/upload/kmz/");
					} else if (attachmentFilename.endsWith(".gpx")){
						filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.GPX_UPLOAD_PATH,"/opt/data/nics/upload/gpx");
					} else if (attachmentFilename.endsWith(".json") || attachmentFilename.endsWith(".geojson")){
					 	filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.JSON_UPLOAD_PATH,"/opt/data/nics/upload/geojson");
					} else if (attachmentFilename.toLowerCase().endsWith(".kml")){
						filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.KML_UPLOAD_PATH,"/opt/data/nics/upload/kml");
					}
					
					if(filePath != null){
						doc = getDocument(attachment, Paths.get(filePath));
					}
				}
			}
			
			if(doc != null){
				
				doc.setUsersessionid(datalayer.getUsersessionid());
				doc = documentDao.addDocument(doc);
				
				dataSourceId = getFileDatasourceId(fileExt);
				
				if (dataSourceId != null) {
					datalayer.setCreated(new Date());
					datalayer.getDatalayersource().setCreated(new Date());
					datalayer.getDatalayersource().setDatasourceid(dataSourceId);
					datalayer.getDatalayersource().setRefreshrate(numericRefreshRate);
				}
				
				String docFilename = doc.getFilename();
				
				if (uploadedDataLayer = docFilename.toLowerCase().endsWith(".kmz")) {
                    String kmlFileName = null;
					logger.debug("Filepath=" + filePath);
					logger.debug("doc.getFilename=" + doc.getFilename());
					String subdir = docFilename.substring(0, docFilename.length() - 4);
					String kmzUploadPath = APIConfig.getInstance().getConfiguration().getString(APIConfig.KMZ_UPLOAD_PATH,"/opt/data/nics/upload/kmz/"); 
					logger.debug("kmzDirectoryPathString=" + kmzUploadPath);
					Path kmzDir = Paths.get(kmzUploadPath, subdir);
					logger.debug("kmzDir=" + kmzDir);
					if (! Files.exists(kmzDir))
						Files.createDirectory(kmzDir);
						
					try (
						
						FileInputStream fis = new FileInputStream(filePath + doc.getFilename());
						ZipInputStream zipStream = new ZipInputStream(fis)
					) {
						ZipEntry entry;
						
						// Stream all KMZ entries into new files under this temp dir.
						while((entry = zipStream.getNextEntry()) != null) {
							if (entry.getSize() == 0){ 				             
								 continue; 				            
							}
							
							String entryName = entry.getName();
							Path outPath = kmzDir.resolve(entryName);
				            
				            if (entryName.toLowerCase().endsWith(".kml")) {
                                kmlFileName = entryName;
                                fileName = entryName;
                            } else {
                                fileName = null;
                            }
				            	
				            if (entryName.contains("/"))
				            	Files.createDirectories(outPath.getParent());
				            	
				            try (
				            	OutputStream output = Files.newOutputStream(outPath)
				            ) {
				            	// KML files may require some translation, to workaround broken input files.
				            	if (fileName != null)
				            		copyKmlStream(outPath, zipStream, output);
				            	// Just copy the content directly, without translation.
				            	else
				            		IOUtils.copy(zipStream, output);
				            }
				       }
						Path oldPath = Paths.get(filePath + docFilename);
						logger.debug("oldPath=" + oldPath);
						Path newPath = Paths.get(kmzUploadPath + "/" + docFilename.toLowerCase());
						logger.debug("newPath=" + newPath);
						Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
						// Set proper file permissions on this file.
						Files.setPosixFilePermissions(newPath, EnumSet.of(
								PosixFilePermission.OWNER_READ,
								PosixFilePermission.OWNER_WRITE,
								PosixFilePermission.GROUP_READ,
								PosixFilePermission.GROUP_WRITE,
								PosixFilePermission.OTHERS_READ
							));
					}
					
					catch(IOException ex) {
						logger.error("Failed to unzip file", ex);
						uploadedDataLayer = false;
						FileUtils.deleteDirectory(kmzDir.toFile ());
			        } finally {
			        	
			        }
				
					// Set the final file name of the data layer.
					fileName = subdir + "/" + kmlFileName;
				}
				else if(uploadedDataLayer = docFilename.endsWith(".gpx")){
					fileName = doc.getFilename();
				}
				else if(uploadedDataLayer = docFilename.endsWith(".json")){
					fileName = doc.getFilename();
				}else if(uploadedDataLayer = docFilename.endsWith(".geojson")){
					fileName = doc.getFilename();
				}
				else if(uploadedDataLayer = docFilename.toLowerCase().endsWith(".kml")){
					fileName = doc.getFilename();
				}
				
			}
			
			if (uploadedDataLayer) {
				datalayer.getDatalayersource().setLayername(fileName);
				
				String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);
				
				Rootfolder folder = folderDao.getRootFolder("Data", workspaceId);
				int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folder.getFolderid());
					
				datalayerDao.insertDataLayerFolder(folder.getFolderid(), datalayerId, nextFolderIndex);
				newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folder.getFolderid());

				datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
				datalayerResponse.setMessage("ok");
				datalayerResponse.setSuccess(true);
				response = Response.ok(datalayerResponse).status(Status.OK).build();
				
			}
			else{
				datalayerResponse.setSuccess(false);
				datalayerResponse.setMessage("Failed to Upload file.");
				response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
		catch(Exception e) {
			logger.error("Failed to insert data layer", e);
			datalayerResponse.setSuccess(false);
			datalayerResponse.setMessage("Failed to add data layer.");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyNewChange(newDatalayerFolder, workspaceId);
			} catch (IOException e) {
				logger.error("Failed to publish DatalayerService message event", e);
			}
		}
		
		return response;
	}

	public Response getToken(String url, String username, String password){
		return Response.ok(this.requestToken(url, username, password).readEntity(String.class)).build();
	}

	public Response getToken(String datasourceId){
        List<Map<String, Object>> data;
        if(StringUtils.isBlank(datasourceId)) {
            return Response.status(Status.BAD_REQUEST).entity("datasourceId is required to request token").build();
        }
        try {
            data = datalayerDao.getAuthentication(datasourceId);
        } catch(Exception e) {
            logger.error("Failed to fetch authentication details from dataSource with Id : " + datasourceId);
            return Response.serverError().entity("Failed to fetch dataSource authentication details with Id : " + datasourceId + e.getMessage()).build();
        }

		if(!CollectionUtils.isEmpty(data) && data.get(0) != null){
			Response response = this.requestToken((String) data.get(0).get(SADisplayConstants.INTERNAL_URL), (String) data.get(0).get(SADisplayConstants.USER_NAME), (String) data.get(0).get(SADisplayConstants.PASSWORD));
            return response;
		} else {
            return Response.serverError().entity("Authentication details not found for dataSource with Id : " + datasourceId).build();
        }
	}

    
	private String buildGenerateTokenUrl(String internalUrl) {
        if(StringUtils.isBlank(internalUrl)) {
            return null;
        }
        int index = (internalUrl.indexOf("rest/services") > -1) ? internalUrl.indexOf("rest/services") : internalUrl.indexOf("services");
        if (index == -1) {
            return null;
        }
        StringBuffer generateTokenUrl = new StringBuffer(internalUrl.substring(0, index));
        generateTokenUrl.append("tokens/generateToken");
        return generateTokenUrl.toString();
    }

	private Response requestToken1(String internalUrl, String username, String password) {
        String generateTokenUrl  = this.buildGenerateTokenUrl(internalUrl);
        if(generateTokenUrl == null) {
            logger.error("Unable to construct generateToken request Url from service with internalUrl " + internalUrl);
            return Response.serverError().entity("Unable to construct generateToken request Url from service with internalUrl : " + internalUrl).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        Map<String, String> requestParams = new HashMap<String, String>();
        Form form = new Form();
        form.param("username", username);
        form.param("password", password);
        form.param("f", "json");

        try {
            WebTarget target = jerseyClient.target(generateTokenUrl.toString());
            Builder builder = target.request(MediaType.APPLICATION_JSON_TYPE);
            Response response = builder.post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE), Response.class);
            return response;
        } catch(Exception e){
            logger.error("Failed to generate token from service url : " + generateTokenUrl, e);
            return Response.serverError().entity("Unable to generate token from service url: " + generateTokenUrl + ", Error: " + e.getMessage()).build();
        }
	}

    private Response requestToken(String internalUrl, String username, String password){
        int index = internalUrl.indexOf("rest/services");
        if(index == -1){
            index = internalUrl.indexOf("services");
        }

        if(index > -1){
            StringBuffer url = new StringBuffer(internalUrl.substring(0, index));
            url.append("tokens/generateToken?");
            url.append("username=");
            url.append(username);
            url.append("&password=");
            url.append(password);
            url.append("&f=json");

            try {
                WebTarget target = jerseyClient.target(url.toString());
                Builder builder = target.request("json");
                Response response =  builder.get();
                return response;
            } catch(Exception e){
                logger.error("Failed to generate token from service url : " + url, e);
                return Response.serverError().entity("Unable to generate token from service url: " + url + ", Error: " + e.getMessage()).build();
            }
        }

        logger.error("Unable to construct generateToken request Url from service with internalUrl " + internalUrl);
        return Response.serverError().entity("Unable to construct generateToken request Url from service with internalUrl : " + internalUrl).status(Status.INTERNAL_SERVER_ERROR).build();
    }
	
	private byte[] writeAttachmentWithDigest(Attachment attachment, Path path, String digestAlgorithm) throws IOException, NoSuchAlgorithmException {
		try(
			InputStream is = attachment.getDataHandler().getInputStream();
		) {
			MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
			
			String ext = getFileExtension(attachment);
			if ("kml".equalsIgnoreCase(ext)) {
				try(
					OutputStream os = Files.newOutputStream(path);
					DigestOutputStream dos = new DigestOutputStream(os, md)
				) {
					copyKmlStream(path, is, dos);
				}
			}
			
			else {
				try (
					DigestInputStream dis = new DigestInputStream(is, md)
				) {
					Files.copy(dis, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			
			return md.digest();	
		}
	}
	
	private Document getDocument(Attachment attachment, Path directory) {
		Path tempPath = null, path = null;
		
		try {
			logger.debug("directory=" + directory);
			Files.createDirectories(directory);

			tempPath = Files.createTempFile(directory, null, null);
			logger.debug("tempPath" + tempPath.toString());
			byte[] digest = writeAttachmentWithDigest(attachment, tempPath, "MD5");
			
			String filename = new BigInteger(1, digest).toString();
			logger.debug("filename=" + filename);
			String ext = getFileExtension(attachment);
			if (ext != null) {
				filename += "." + ext;
			}
			path = directory.resolve(filename);
			logger.debug("path=" + path.toString());
			path = Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
			logger.debug("path=" + path.toString());
			
			// Set proper file permissions on this file.
			Files.setPosixFilePermissions(path, EnumSet.of(
					PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE,
					PosixFilePermission.GROUP_READ,
					PosixFilePermission.GROUP_WRITE,
					PosixFilePermission.OTHERS_READ
				));
		} catch (IOException|NoSuchAlgorithmException e) {
			logger.error("Failed to save file attachment", e);
			return null;
		} finally {
			//cleanup files
			if (tempPath != null) {
				File file = tempPath.toFile();
				if (file.exists()) {
					file.delete();
				}
			}
		}
		
		Document doc = new Document();
		doc.setDisplayname(attachment.getContentDisposition().getParameter("filename"));
		doc.setFilename(path.getFileName().toString());
		doc.setFiletype(attachment.getContentType().toString());
		doc.setCreated(new Date());
		return doc;
	}

	/** Utility method for copying (and possibly translating) a KML input stream to an output stream. */
	public void copyKmlStream(Path filePath, InputStream input, OutputStream output) throws IOException {
	    KmlFormatter formatter = new KmlFormatter();
	    formatter.format(input,output);
	    Charset charset = StandardCharsets.UTF_8;
	    String fileData = new String(Files.readAllBytes(filePath), charset);
	    String modifiedKml = formatter.fixCommonKmlIssues(fileData);
	    Files.write(filePath, modifiedKml.getBytes(charset));
	}
	
	private String getMapserverDatasourceId() {
		if(this.getMapServerURL() == null) {
			return null;
		}
		String wmsMapserverURL = this.getMapServerURL().concat("/wms");
		
		String datasourceId = datalayerDao.getDatasourceId(wmsMapserverURL);
		if (datasourceId == null) {
			int datasourcetypeid = datalayerDao.getDatasourceTypeId("wms");
			if (datasourcetypeid != -1) {
				Datasource ds = new Datasource();
				ds.setInternalurl(wmsMapserverURL);
				ds.setDatasourcetypeid(datasourcetypeid);
				ds.setDisplayname("NICS WMS Server");
				datasourceId = datalayerDao.insertDataSource(ds);
			}
		}
		return datasourceId;
	}
	
	private String getFileDatasourceId(String fileExt) {
		if(this.getWebServerURL() == null) {
			return null;
		}
		String webServerURL = this.getWebServerURL().concat("/" + fileExt + "/");
		
		String datasourceId = datalayerDao.getDatasourceId(webServerURL);
		if (datasourceId == null) {
			int datasourcetypeid = datalayerDao.getDatasourceTypeId(fileExt);
			if (datasourcetypeid != -1) {
				Datasource ds = new Datasource();
				ds.setInternalurl(webServerURL);
				ds.setDatasourcetypeid(datasourcetypeid);
				datasourceId = datalayerDao.insertDataSource(ds);
			}
		}
		return datasourceId;
	}
	
	private GeoServer getGeoServer(Configuration config) {
		String geoserverUrl = config.getString(APIConfig.EXPORT_MAPSERVER_URL);
		if (geoserverUrl == null) {
			logger.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_URL);
		}
		
		String geoserverUsername = config.getString(APIConfig.EXPORT_MAPSERVER_USERNAME);
		if (geoserverUsername == null) {
			logger.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_USERNAME);
		}
		
		String geoserverPassword = config.getString(APIConfig.EXPORT_MAPSERVER_PASSWORD);
		if (geoserverPassword == null) {
			logger.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_PASSWORD);
		}
		
		return new GeoServer(geoserverUrl.concat(APIConfig.EXPORT_REST_URL), geoserverUsername, geoserverPassword);
	}
	
	private String getFileExtension(Attachment attachment) {
		String filename = attachment.getContentDisposition().getParameter("filename");
		
		int idx = filename.lastIndexOf(".");
		if (idx != -1) {
			return filename.substring(idx + 1);
		}
		return null;
	}
	
	private void notifyNewChange(Datalayerfolder datalayerfolder, int workspaceId) throws IOException {
		if (datalayerfolder != null) {
			String topic = String.format("iweb.NICS.%s.datalayer.new", workspaceId);
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(datalayerfolder);
			getRabbitProducer().produce(topic, message);
		}
	}
	
	private void notifyDeleteChange(String dataSourceId) throws IOException {
		if (dataSourceId != null) {
			String topic = String.format("iweb.NICS.datalayer.delete");
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(dataSourceId);
			getRabbitProducer().produce(topic, message);
		}
	}
	
	private void notifyUpdateChange(Datalayer datalayer) throws IOException {
		if (datalayer != null) {
			String topic = String.format("iweb.NICS.datalayer.update");
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(datalayer);
			getRabbitProducer().produce(topic, message);
		}
	}
	
	private RabbitPubSubProducer getRabbitProducer() throws IOException {
		if (rabbitProducer == null) {
			rabbitProducer = RabbitFactory.makeRabbitPubSubProducer(
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_HOSTNAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_EXCHANGENAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERNAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERPWD_KEY));
		}
		return rabbitProducer;
	}

	private Response getInvalidResponse(){
		return Response.status(Status.BAD_REQUEST).entity(
				Status.FORBIDDEN.getReasonPhrase()).build();
	}

	/**
	 * Parses refresh rate presented as a string into an integer. If the string cannot be parsed, logs a warning and
	 * returns a default value.
	 *
	 * @param refreshRate The refresh rate as a string
	 * @return The refresh rate as an integer
	 */
	private int parseRefreshRate(String refreshRate) {
		try {
			return Integer.parseInt(refreshRate);
		} catch (NumberFormatException nfe) {
			logger.warn(String.format("Unable to parse %s, defaulting to 300 seconds", refreshRate));
			return 300;
		}
	}
}
