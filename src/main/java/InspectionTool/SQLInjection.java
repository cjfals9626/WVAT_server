package InspectionTool;

import Persistence.DAO.PayLoadDAO;
import Persistence.DTO.PayLoadDTO;
import Persistence.MybatisConnectionFactory;
import lombok.SneakyThrows;
import lombok.Synchronized;
import org.jnetpcap.Pcap;
import org.jnetpcap.PcapHeader;
import org.jnetpcap.PcapIf;
import org.jnetpcap.nio.JBuffer;
import org.jnetpcap.nio.JMemory;
import org.jnetpcap.packet.JRegistry;
import org.jnetpcap.packet.Payload;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.protocol.lan.Ethernet;
import org.jnetpcap.protocol.network.Ip4;
import org.jnetpcap.protocol.tcpip.Http;
import org.jnetpcap.protocol.tcpip.Tcp;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SQLInjection extends Vulnerability implements Runnable {
    public SQLInjection() {
        super();
    }

    static int stateCode = 0;
    static boolean isFlag = true;
    static boolean isCapture = true;

    @Override
    public void check(String domain) {
        runModule(domain);
    }

    @Override
    public void run() {
        packetDump();
    }

    @Override
    public void runModule(String domain) {
        PayLoadDAO payLoadDAO = new PayLoadDAO(MybatisConnectionFactory.getSqlSessionFactory());
        List<PayLoadDTO> payLoadDTO = payLoadDAO.randomAndOrderPayLoadSelectPrint("sqlInjection");
        String res = " ";
        boolean isSqlInjection = false;
        setSeleniumConfigSetting();

        getDriver().get(domain);

        try {
            List<WebElement> elements = getDriver().findElements(new By.ByTagName("a"));
            for(WebElement e : elements){
                if(e.getAttribute("href").equals(domain+"/#acField") || e.getAttribute("href").equals(domain+"/#myModal")){
                    e.click();
                }
            }

            elements = getDriver().findElements(By.tagName("input"));

            for (int i = 0; i < elements.size(); i++) {
                elements = getDriver().findElements(By.tagName("input"));
                if (elements.get(i).getAttribute("type").equals("text")) {
                    if (elements.get(i).getAttribute("name").contains("user") || elements.get(i).getAttribute("name").contains("id") || elements.get(i).getAttribute("name").contains("uname")) {
                        for (int j = 0; j < payLoadDTO.size(); j++) {
                            elements = getDriver().findElements(By.tagName("input"));
                            elements.get(i).sendKeys(payLoadDTO.get(j).getPayload());

                            for(int k =0; k < elements.size(); k++){
                                if(elements.get(k).getAttribute("type").equals("password")){
                                    elements.get(k).sendKeys("@@S23sd23");
                                    elements.get(k).sendKeys(Keys.ENTER);
                                    break;
                                }
                            }

                            System.out.println(stateCode);
                            if (stateCode == 302) {
                                stateCode = 0;
                                getDriver().navigate().refresh();

                                elements = getDriver().findElements(new By.ByTagName("a"));
                                for(WebElement e : elements){
                                    if(e.getAttribute("href").equals(domain+"/#acField") || e.getAttribute("href").equals(domain+"/#myModal")){
                                        e.click();
                                    }
                                }
                                continue;
                            }else if(!(ExpectedConditions.alertIsPresent().apply(driver)==null)){
                                driver.switchTo().alert().accept();
                                break;
                            }else{
                                res = payLoadDTO.get(j).getPayload();
                                elements = getDriver().findElements(By.tagName("input"));
                                payLoadDAO.updatePayLoadCount(payLoadDTO.get(j).getPayload());
                                isSqlInjection = true;
                                break;
                            }
                        }
                    }
                }
                if (isSqlInjection) break;
            }

            if (isSqlInjection) {
                setInputValue(res);
                setResult("200 OK");
                setExists(true);
                setStateCode("200");
            } else {
                setInputValue("Blind SQL Injection Syntax");
                setResult(" ");
                setExists(false);
                isCapture = false;
            }
        } catch (Exception e) {
            System.out.println("SQL Exception");
            e.printStackTrace();
        } finally {
            isCapture = false;
            getDriver().close();
            getDriver().quit();
        }
    }

    public void packetDump() {
        ArrayList<PcapIf> allDevs = new ArrayList<PcapIf>();
        // ??????????????? ?????? ????????? ArrayList??? ??????
        StringBuilder errbuf = new StringBuilder();
        // ????????????

        int r = Pcap.findAllDevs(allDevs, errbuf);
        // ??????????????? ??????????????? ??? ??????????????? ?????????, ??? ??????????????? ????????????

        if (r == Pcap.NOT_OK || allDevs.isEmpty()) {
            System.out.println("???????????? ?????? ?????? ??????." + errbuf.toString());
            return;
        }    // ????????????

        System.out.println("< ????????? ???????????? Device >");

        int myDevice = 0;
        for (int i = 0; i < allDevs.size(); i++) {    // ????????? ????????? ??????
            String description = (allDevs.get(i).getDescription() != null) ? allDevs.get(i).getDescription() : "????????? ?????? ????????? ????????????.";
            System.out.printf("[%d???]: %s [%s]\n", i, allDevs.get(i).getName(), description);
            if (allDevs.get(i).getName().equals("\\Device\\NPF_{B4E6BF99-FAB4-4EBB-B396-2FEB755C6B97}")) //?????? ????????????
                myDevice = i; //??? ???????????? ??????
        }

        PcapIf device = allDevs.get(myDevice);
        System.out.printf("????????? ??????: %s\n", (device.getDescription() != null) ?
                device.getDescription() : device.getName());

        int snaplen = 64 * 1024; //65536Byte?????? ????????? ??????
        int flags = Pcap.MODE_NON_PROMISCUOUS; // ???????????????
        int timeout = 10 * 500; // timeout 5second
        Pcap pcap = Pcap.openLive(device.getName(), snaplen, flags, timeout, errbuf);

        if (pcap == null) {
            System.out.println("Network Device Access Failed. Error: " + errbuf.toString());
            return;
        }

        // ????????? ?????? ????????? ?????? ??????
        Http http = new Http();
        PcapHeader header = new PcapHeader(JMemory.POINTER);
        JBuffer buf = new JBuffer(JMemory.POINTER);

        int id = JRegistry.mapDLTToId(pcap.datalink());    // pcap??? datalink ????????? jNetPcap??? ???????????? ID??? ??????
        isFlag = false;
        isCapture = true;

        while (pcap.nextEx(header, buf) == Pcap.NEXT_EX_OK) // ????????? ????????? ?????????
        {
            if(!isCapture) break;

            PcapPacket packet = new PcapPacket(header, buf);
            packet.getCaptureHeader();

            packet.scan(id); //????????? ????????? ???????????? ?????? ??? ????????? ????????????

            if (packet.hasHeader(http)) {
                String code = " ";
                code = http.fieldValue(Http.Response.ResponseCode);
                if (code != null && code.equals("302")) {
                    stateCode = 302;
                }else if(code != null && code.equals("200") || code == null){
                    continue;
                }else{
                    break;
                }
            }
        }
        pcap.close();
    }
}