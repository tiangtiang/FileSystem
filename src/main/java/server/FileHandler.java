package server;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.stream.ChunkedFile;

import javax.activation.MimetypesFileTypeMap;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by tiang on 2018/5/7.
 */
public class FileHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object o) throws Exception {
        if(o instanceof FullHttpRequest){
            //获取完整的http请求
            FullHttpRequest request = (FullHttpRequest) o;
            System.out.println(request.uri());
            String url = request.uri();
            HttpResponseStatus status = OK;
            String result = "success";
            String contentType = "text/plain";

            if(request.method() == HttpMethod.POST && url.equals("/upload")) {
                //上传文件
                if(!handleUpload(request, ctx)) return;
            }else if(request.method() == HttpMethod.GET && url.contains("/download")){
                QueryStringDecoder decoder = new QueryStringDecoder(url);
                String path = null;
                for(Map.Entry<String, List<String>> entry: decoder.parameters().entrySet()) {
                    if(entry.getKey().equals("path")){
                        path = entry.getValue().get(0);
                        break;
                    }
                }
                if(path == null){
                    if(url.length()>10)
                        path = url.substring(10);
                }
                if(path == null){
                    status = HttpResponseStatus.BAD_REQUEST;
                    result = "lost parameter";
                }else {
                    if(!path.startsWith("file/"))
                        path = "file/"+path;
                    File pathFile = new File(path);
                    if(pathFile.exists()){
                        //如果是目录
                        if(pathFile.isDirectory()){
                            //列出目录下的所有文件
                            List<String> files = new ArrayList<>();
                            for (File f:
                                 pathFile.listFiles()) {
                                files.add(f.getName());
                            }
                            result = new Gson().toJson(files);
                            contentType = "application/json";
                        }else{
                            //如果是文件
                            if(!downloadFile(request, ctx, pathFile)){
                                status = HttpResponseStatus.NOT_FOUND;
                                result = "not found";
                            }
                        }
                    }else{
                        status = HttpResponseStatus.BAD_REQUEST;
                        result = "file not exist";
                    }
                }
            }else{
                status = HttpResponseStatus.NOT_FOUND;
                result = "not found";
            }
            //发送响应
            sendResponse(ctx, status, result, contentType);
        }
    }
    private boolean downloadFile(FullHttpRequest request, ChannelHandlerContext ctx, File pathFile) throws IOException {
        //传输文件
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(pathFile, "r");// 以只读的方式打开文件
        } catch (FileNotFoundException fnfe) {
            return false;
        }
        long fileLength = randomAccessFile.length();
        //创建一个默认的HTTP响应
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        //设置Content Length
        HttpUtil.setContentLength(response, fileLength);
        //设置Content Type
        setContentTypeHeader(response, pathFile);
        //如果request中有KEEP ALIVE信息
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.write(response);
        ChannelFuture sendFileFuture;
        //通过Netty的ChunkedFile对象直接将文件写入发送到缓冲区中
        sendFileFuture = ctx.write(new ChunkedFile(randomAccessFile, 0,
                fileLength, 8192), ctx.newProgressivePromise());
        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture future,
                                            long progress, long total) {
                if (total < 0) { // total unknown
                    System.err.println("Transfer progress: " + progress);
                } else {
                    System.err.println("Transfer progress: " + progress + " / "
                            + total);
                }
            }

            @Override
            public void operationComplete(ChannelProgressiveFuture future)
                    throws Exception {
                System.out.println("Transfer complete.");
            }
        });
        ChannelFuture lastContentFuture = ctx
                .writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        return true;
    }

    private void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaderNames.CONTENT_TYPE,
                mimeTypesMap.getContentType(file.getPath()));
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String result, String contentType){
        //发送成功响应
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,
                status, Unpooled.wrappedBuffer(result.getBytes()));
        response.headers().set(CONTENT_TYPE, contentType);
        response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());
        //关闭连接
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private boolean handleUpload(FullHttpRequest request, ChannelHandlerContext ctx) throws IOException {
        //解析Http请求中的参数
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
        //获取学年
        String year = ((Attribute) decoder.getBodyHttpData("year")).getValue();
        //获取学期
        String term = ((Attribute) decoder.getBodyHttpData("term")).getValue();
        // 获取课头号
        String lessonHeadid = ((Attribute)decoder.getBodyHttpData("lessonHeadId")).getValue();
        //以学年_学期为目录
        DiskFileUpload.baseDirectory = Config.basePath+year + '_' + term;
        //如果该目录不存在就创建
        File dir = new File(DiskFileUpload.baseDirectory);
        if (!dir.exists()) {
            boolean isOk = dir.mkdirs();
            System.out.println(isOk);
        }

        //寻找文件数据
        List<InterfaceHttpData> datas = decoder.getBodyHttpDatas();
        for (InterfaceHttpData data : datas) {
            if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                FileUpload fileUpload = (FileUpload) data;
                String fileName = fileUpload.getFilename();
                String types;
                if((types = isCorrectFile(fileName))==null){
                    fileUpload.delete();
                    sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, "incorrect file type", "text/plain");
                    return false;
                }
                if (fileUpload.isCompleted()) {
                    //保存到磁盘
                    StringBuffer fileNameBuf = new StringBuffer();
                    fileNameBuf.append(DiskFileUpload.baseDirectory).append(File.separator).append(lessonHeadid)
                            .append(types);
                    fileUpload.renameTo(new File(fileNameBuf.toString()));
                }
            }
        }
        return true;
    }

    /**
     * 判断是否是允许的文件类型
     * @param fileName
     * @return
     */
    private String isCorrectFile(String fileName){
        for(String types : Config.fileTypes){
            if(fileName.endsWith(types))
                return types;
        }
        return null;
    }

    //发生异常时触发
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println(cause.getMessage());
        //发送失败响应
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer(cause.getMessage().getBytes()));
        response.headers().set(CONTENT_TYPE, "text/plain");
        response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response);
    }
}
