package com.zld.struts.marketer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.zld.AjaxUtil;
import com.zld.impl.MongoClientFactory;
import com.zld.service.DataBaseService;
import com.zld.service.PgOnlyReadService;
import com.zld.utils.RequestUtil;
import com.zld.utils.StringUtils;

public class DownloadPicsAction extends Action {
	@Autowired
	private DataBaseService daService;
	@Autowired
	private PgOnlyReadService pService;
	
	private Logger logger = Logger.getLogger(DownloadPicsAction.class);
	public ActionForward execute(ActionMapping mapping,ActionForm form,HttpServletRequest request,HttpServletResponse response) throws Exception{
		String action = RequestUtil.processParams(request, "action");
		String token =RequestUtil.processParams(request, "token");
		Map<String,Object> infoMap  = new HashMap<String, Object>();
		if(token==null||"null".equals(token)||"".equals(token)){
			infoMap.put("result", "fail");
			infoMap.put("message", "token无效!");
			AjaxUtil.ajaxOutput(response, StringUtils.createJson(infoMap));
			return null;
		}
		Long uid = validToken(token);
		if(uid == null){
			infoMap.put("result", "fail");
			infoMap.put("message", "token无效!");
			AjaxUtil.ajaxOutput(response, StringUtils.createJson(infoMap));
			return null;
		}
		if(action.equals("downloadparkpics")){
			Long comid = RequestUtil.getLong(request, "comid", -1L);
			if(comid == -1){
				AjaxUtil.ajaxOutput(response, "-1");
				return null;
			}
			downloadParkPics(comid,request,response);
			//http://192.168.199.239/zld/download.do?action=downloadparkpics&comid=1204&token=e6c435a27cf1f4a11d11c56d0cebc614
		}else if(action.equals("updatevisitpics")){
			Long visitid = RequestUtil.getLong(request, "id", -1L);
			if(visitid == -1){
				AjaxUtil.ajaxOutput(response, "-1");
				return null;
			}
			String r = updateVisitPic2Mongodb(request, response, uid, visitid);
			if(!r.equals("1")){
				String address = AjaxUtil.decodeUTF8(RequestUtil.processParams(request, "address"));
				String sql = "update visit_info_tb set address=? where id=?";
				int result = daService.update(sql, new Object[]{address,visitid});
				AjaxUtil.ajaxOutput(response, result+"");
			}else{
				AjaxUtil.ajaxOutput(response, "-1");
			}
			//http://192.168.199.239/zld/download.do?action=updatevisitpics&id=&address=&token=e6c435a27cf1f4a11d11c56d0cebc614
		}else if(action.equals("downloadvisitpics")){
			Long visitid = RequestUtil.getLong(request, "id", -1L);
			downloadVisitPics(visitid, request, response);
			//http://192.168.199.239/zld/download.do?action=downloadvisitpics&id=&token=e6c435a27cf1f4a11d11c56d0cebc614
		}else if(action.equals("uploadvisitpics")){
			Long visitid = daService.getLong("SELECT nextval('seq_visit_info_tb'::REGCLASS) AS newid", null);
			String r = uploadVisitPic2Mongodb(request, response, uid, visitid);
			if(!r.equals("1")){
				AjaxUtil.ajaxOutput(response, "-1");
			}else{
				AjaxUtil.ajaxOutput(response, visitid+"");
			}
			//http://192.168.199.239/zld/download.do?action=uploadvisitpics&token=e6c435a27cf1f4a11d11c56d0cebc614
		}
		return null;
	}
	
	/**
	 * 验证token是否有效
	 * @param token
	 * @return uin
	 */
	private Long validToken(String token) {
		Map tokenMap = pService.getMap("select * from user_session_tb where token=?", new Object[]{token});
 		Long uin = null;
		if(tokenMap!=null&&tokenMap.get("uin")!=null){
			uin = (Long) tokenMap.get("uin");
		}
		return uin;
	}
	
	private void downloadParkPics (Long comid,HttpServletRequest request,HttpServletResponse response) throws Exception{
		logger.error("download parkPics from mongodb....");
		System.err.println("downloadParkPics from mongodb file:comid="+comid);
		if(comid!=null){
			DB db = MongoClientFactory.getInstance().getMongoDBBuilder("zld");//
			DBCollection collection = db.getCollection("park_pics");
			BasicDBObject document = new BasicDBObject();
			BasicDBObject condation = new BasicDBObject();
			document.put("comid", comid);
			//按生成时间查最近的数据
			condation.put("ctime", -1);
			DBCursor objs = collection.find(document).sort(condation).limit(1);
			DBObject obj = null;
			while (objs.hasNext()) {
				obj = objs.next();
			}
			if(obj == null){
				AjaxUtil.ajaxOutput(response, "");
				return;
			}
			byte[] content = (byte[])obj.get("content");
			db.requestDone();
			response.setDateHeader("Expires", System.currentTimeMillis()+4*60*60*1000);
			//response.setStatus(httpc);
			Calendar c = Calendar.getInstance();
			c.set(1970, 1, 1, 1, 1, 1);
			response.setHeader("Last-Modified", c.getTime().toString());
			response.setContentLength(content.length);
			response.setContentType("image/jpeg");
		    OutputStream o = response.getOutputStream();
		    o.write(content);
		    o.flush();
		    o.close();
		    response.flushBuffer();
		    //response.reset();
		    System.out.println("mongdb over.....");
		}else {
			AjaxUtil.ajaxOutput(response, "-1");
		}
	}
	
	private void downloadVisitPics (Long visitid,HttpServletRequest request,HttpServletResponse response) throws Exception{
		logger.error("download visitPics from mongodb....");
		System.err.println("downloadVisitPics from mongodb file:visitid="+visitid);
		if(visitid!=null){
			DB db = MongoClientFactory.getInstance().getMongoDBBuilder("zld");//
			DBCollection collection = db.getCollection("visit_pics");
			BasicDBObject document = new BasicDBObject();
			BasicDBObject condation = new BasicDBObject();
			document.put("visitid", visitid);
			//按生成时间查最近的数据
			condation.put("ctime", -1);
			DBCursor objs = collection.find(document).sort(condation).limit(1);
			DBObject obj = null;
			while (objs.hasNext()) {
				obj = objs.next();
			}
			if(obj == null){
				AjaxUtil.ajaxOutput(response, "");
				return;
			}
			byte[] content = (byte[])obj.get("content");
			db.requestDone();
			response.setDateHeader("Expires", System.currentTimeMillis()+4*60*60*1000);
			//response.setStatus(httpc);
			Calendar c = Calendar.getInstance();
			c.set(1970, 1, 1, 1, 1, 1);
			response.setHeader("Last-Modified", c.getTime().toString());
			response.setContentLength(content.length);
			response.setContentType("image/jpeg");
		    OutputStream o = response.getOutputStream();
		    o.write(content);
		    o.flush();
		    o.close();
		    response.flushBuffer();
		    //response.reset();
		    System.out.println("mongdb over.....");
		}else {
			AjaxUtil.ajaxOutput(response, "-1");
		}
	}
	
	/*
	 * 更新拜访图片
	 */
	private String updateVisitPic2Mongodb (HttpServletRequest request,HttpServletResponse response,Long uid,Long visitid) throws Exception{
		logger.error("begin update Visit picture....");
		Map<String, String> extMap = new HashMap<String, String>();
	    extMap.put(".jpg", "image/jpeg");
	    extMap.put(".jpeg", "image/jpeg");
	    extMap.put(".png", "image/png");
	    extMap.put(".gif", "image/gif");
		request.setCharacterEncoding("UTF-8"); // 设置处理请求参数的编码格式
		DiskFileItemFactory  factory = new DiskFileItemFactory(); // 建立FileItemFactory对象
		factory.setSizeThreshold(16*4096*1024);
		ServletFileUpload upload = new ServletFileUpload(factory);
		// 分析请求，并得到上传文件的FileItem对象
		upload.setSizeMax(16*4096*1024);
		List<FileItem> items = null;
		try {
			items =upload.parseRequest(request);
		} catch (FileUploadException e) {
			e.printStackTrace();
			return "-1";
		}
		String filename = ""; // 上传文件保存到服务器的文件名
		InputStream is = null; // 当前上传文件的InputStream对象
		// 循环处理上传文件
		for (FileItem item : items){
			if (!item.isFormField()){
				if (item.getName() != null && !item.getName().equals("")){// 处理上传文件
					// 从客户端发送过来的上传文件路径中截取文件名
					logger.error(item.getName());
					filename = item.getName().substring(
							item.getName().lastIndexOf("\\")+1);
					is = item.getInputStream(); // 得到上传文件的InputStream对象
					}
				}
		}
		String file_ext =filename.substring(filename.lastIndexOf(".")).toLowerCase();// 扩展名
		BufferedInputStream in = null;  
		ByteArrayOutputStream byteout =null;
	    try {
	    	in = new BufferedInputStream(is);   
	    	byteout = new ByteArrayOutputStream(1024);        	       
		      
	 	    byte[] temp = new byte[1024];        
	 	    int bytesize = 0;        
	 	    while ((bytesize = in.read(temp)) != -1) {        
	 	          byteout.write(temp, 0, bytesize);        
	 	    }        
	 	      
	 	    byte[] content = byteout.toByteArray(); 
	 	    DB mydb = MongoClientFactory.getInstance().getMongoDBBuilder("zld");
		    mydb.requestStart();
			  
		    DBCollection collection = mydb.getCollection("visit_pics");
		  //  DBCollection collection = mydb.getCollection("records_test");
			BasicDBObject updateCondition = new BasicDBObject();
			updateCondition.put("visitid", visitid);
			BasicDBObject updatedValue = new BasicDBObject();
			updatedValue.put("ctime",  System.currentTimeMillis()/1000);
			updatedValue.put("type", extMap.get(file_ext));
			updatedValue.put("content", content);
			BasicDBObject updatedSetValue = new BasicDBObject("$set",updatedValue);
			  //开始事务
			mydb.requestStart();
			collection.update(updateCondition, updatedSetValue);
			  //结束事务
			mydb.requestDone();
			in.close();        
		    is.close();
		    byteout.close();
		} catch (Exception e) {
			e.printStackTrace();
			return "-1";
		}finally{
			if(in!=null)
				in.close();
			if(byteout!=null)
				byteout.close();
			if(is!=null)
				is.close();
		}
	  
		return "1";
	}
	
	/*
	 * 上传拜访图片
	 */
	private String uploadVisitPic2Mongodb (HttpServletRequest request,HttpServletResponse response,Long uid,Long visitid) throws Exception{
		logger.error("begin upload Visit picture....");
		Map<String, String> extMap = new HashMap<String, String>();
	    extMap.put(".jpg", "image/jpeg");
	    extMap.put(".jpeg", "image/jpeg");
	    extMap.put(".png", "image/png");
	    extMap.put(".gif", "image/gif");
		request.setCharacterEncoding("UTF-8"); // 设置处理请求参数的编码格式
		DiskFileItemFactory  factory = new DiskFileItemFactory(); // 建立FileItemFactory对象
		factory.setSizeThreshold(16*4096*1024);
		ServletFileUpload upload = new ServletFileUpload(factory);
		// 分析请求，并得到上传文件的FileItem对象
		upload.setSizeMax(16*4096*1024);
		List<FileItem> items = null;
		try {
			items =upload.parseRequest(request);
		} catch (FileUploadException e) {
			e.printStackTrace();
			return "-1";
		}
		String filename = ""; // 上传文件保存到服务器的文件名
		InputStream is = null; // 当前上传文件的InputStream对象
		// 循环处理上传文件
		for (FileItem item : items){
			if (!item.isFormField()){
				if (item.getName() != null && !item.getName().equals("")){// 处理上传文件
					// 从客户端发送过来的上传文件路径中截取文件名
					logger.error(item.getName());
					filename = item.getName().substring(
							item.getName().lastIndexOf("\\")+1);
					is = item.getInputStream(); // 得到上传文件的InputStream对象
					}
				}
		}
		String file_ext =filename.substring(filename.lastIndexOf(".")).toLowerCase();// 扩展名
		String picurl = uid + "_" + visitid + "_" + System.currentTimeMillis()/1000 + file_ext;
		BufferedInputStream in = null;  
		ByteArrayOutputStream byteout =null;
	    try {
	    	in = new BufferedInputStream(is);   
	    	byteout = new ByteArrayOutputStream(1024);        	       
		      
	 	    byte[] temp = new byte[1024];        
	 	    int bytesize = 0;        
	 	    while ((bytesize = in.read(temp)) != -1) {        
	 	          byteout.write(temp, 0, bytesize);        
	 	    }        
	 	      
	 	    byte[] content = byteout.toByteArray(); 
	 	    DB mydb = MongoClientFactory.getInstance().getMongoDBBuilder("zld");
		    mydb.requestStart();
			  
		    DBCollection collection = mydb.getCollection("visit_pics");
		  //  DBCollection collection = mydb.getCollection("records_test");
			  
			BasicDBObject document = new BasicDBObject();
			document.put("uid",  uid);
			document.put("visitid", visitid);
			document.put("ctime",  System.currentTimeMillis()/1000);
			document.put("type", extMap.get(file_ext));
			document.put("content", content);
			document.put("filename", picurl);
			  //开始事务
			mydb.requestStart();
			collection.insert(document);
			  //结束事务
			mydb.requestDone();
			in.close();        
		    is.close();
		    byteout.close();
		} catch (Exception e) {
			e.printStackTrace();
			return "-1";
		}finally{
			if(in!=null)
				in.close();
			if(byteout!=null)
				byteout.close();
			if(is!=null)
				is.close();
		}
	  
		return "1";
	}
}
