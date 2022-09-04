package InspectionTool;

import Persistence.DAO.PayLoadDAO;
import Persistence.DTO.PayLoadDTO;
import Persistence.MybatisConnectionFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OSCommand extends Vulnerability {
    final static String[] OS_COMMAND_PAYLOAD = {"root :", "daemon :", "bin :"};
    final static String[] OS_COMMAND_PAYLOAD_ID = {"uid", "gid", "groups"};
    final static String[] OS_COMMAND_PAYLOAD_DIR = {"Directory of C", "Windows"};
    final static String[] OS_COMMAND_PAYLOAD_LS = {"rwx", "-wx", "r-x", "rw-", "r--", "-w-", "--x", "---"};
    final static String[] OS_COMMAND_PAYLOAD_PING = {"PING", "32바이트 데이터 사용", "bytes of data."};
    final static String[] SPECIAL_CHARACTERS = {"&", "&&", "`", "(", ")", "|", "||", ";"};
    final static String[] KEYWORD = {"password", "id", "dir", "ls -l", "ping", "specialCharacters"};

    List<String> blackListInputTag = new ArrayList<>();
    List<String> checkInputTag = new ArrayList<>();

    @Override
    public void check(String domain) {
        runModule(domain);
    }

    @Override
    public void runModule(String domain) {
        PayLoadDAO payLoadDAO = new PayLoadDAO(MybatisConnectionFactory.getSqlSessionFactory());
        setExists(false);
        setResult(" ");
        if(specialCharacters(domain)){
            System.out.println("specialCharters");
            if (checkKeyword(payLoadDAO, domain, KEYWORD)) {
                setExists(true);
            }
        }
    }

    public boolean checkInputTag(String attributeValue){ // attribute 값 들어옴 list에 같은 값이 존재하면 true 존재 하지 않으면 false, 추가
        return blackListInputTag.contains(attributeValue);
    }

    public void setCheckInputTag(){
        List<String> result = checkInputTag.stream().distinct().collect(Collectors.toList());
        for(String inputTag : result){
            blackListInputTag.add(inputTag);
        }
        checkInputTag = new ArrayList<>();
    }

    public boolean checkPageSource(String pageSource, String keyword){
        if(keyword.equals(KEYWORD[0])){
            for(String payload : OS_COMMAND_PAYLOAD){
                if(pageSource.contains(payload)){
                    return true;
                }
            }
            return false;
        }else if (keyword.equals(KEYWORD[1])){
            for(String payload : OS_COMMAND_PAYLOAD_ID){
                if(pageSource.contains(payload)){
                    return true;
                }
            }
            return false;
        }else if(keyword.equals(KEYWORD[2])){
            for(String payload : OS_COMMAND_PAYLOAD_DIR){
                if(pageSource.contains(payload)){
                    return true;
                }
            }
            return false;
        }else if(keyword.equals(KEYWORD[3])){
            for(String payload : OS_COMMAND_PAYLOAD_LS){
                if(pageSource.contains(payload)){
                    return true;
                }
            }
            return false;
        }else if(keyword.equals(KEYWORD[4])){
            for(String payload : OS_COMMAND_PAYLOAD_PING){
                if(pageSource.contains(payload)){
                    return true;
                }
            }
            return false;
        }else if(keyword.equals(KEYWORD[5])){
            for(String payload : SPECIAL_CHARACTERS){
                if(pageSource.contains(payload)){
                    return true;
                }
            }
            return false;
        }else
            return false;
    }

    public boolean specialCharacters(String domain){
        setSeleniumConfigSetting();
        getDriver().get(domain);
        String pageSource = "";
        try {
            List<WebElement> elements = getDriver().findElements(By.tagName("input"));
            if(elements.isEmpty())
                return false;
            for (int i = 0; i < elements.size(); i++) {
                elements = getDriver().findElements(By.tagName("input"));
                if(checkInputTag(elements.get(i).getAttribute("name"))){
                    return false;
                }
                if (elements.get(i).getAttribute("type").equals("text")) {
                    if (elements.get(i).getAttribute("name").contains("user") || elements.get(i).getAttribute("name").contains("id") || elements.get(i).getAttribute("name").contains("uname")) {
                        continue;
                    }

                    for (int j = 0; j < SPECIAL_CHARACTERS.length; j++) {
                        elements = getDriver().findElements(By.tagName("input"));
                        elements.get(i).sendKeys(SPECIAL_CHARACTERS[j]);
                        elements.get(i).sendKeys(Keys.ENTER);
                        HttpURLConnection c = (HttpURLConnection) new URL(driver.getCurrentUrl()).openConnection();
                        pageSource = getDriver().getPageSource();
                        if (c.getResponseCode() == 500) {
                            return true;
                        } else if (checkPageSource(pageSource, KEYWORD[5])) {
                            return true;
                        }
                        getDriver().navigate().back();
                        getDriver().navigate().refresh();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("OS Command Injection Special Characters Exception");
            e.printStackTrace();
        } finally {
            getDriver().close();
            getDriver().quit();
        }
        return false;
    }

    public boolean checkKeyword(PayLoadDAO payLoadDAO, String domain, String[] keyword){
        for(int k = 0; k < keyword.length - 1; k++){
            List<PayLoadDTO> payloadDTOS = payLoadDAO.selectOsCommandInjection(keyword[k]);
            String pageSource = "";
            setSeleniumConfigSetting();
            getDriver().get(domain);

            try {
                List<WebElement> elements = getDriver().findElements(By.tagName("input"));
                if(elements.isEmpty())
                    return false;
                for (int i = 0; i < elements.size(); i++) {
                    elements = getDriver().findElements(By.tagName("input"));

                    if(checkInputTag(elements.get(i).getAttribute("name")))
                        return false;
                    else
                        checkInputTag.add(elements.get(i).getAttribute("name"));

                    if (elements.get(i).getAttribute("type").equals("text")) {
                        if (elements.get(i).getAttribute("name").contains("user") || elements.get(i).getAttribute("name").contains("id") || elements.get(i).getAttribute("name").contains("uname")) {
                            continue;
                        }

                        for (int j = 0; j < payloadDTOS.size(); j++) {
                            elements = getDriver().findElements(By.tagName("input"));
                            elements.get(i).sendKeys(payloadDTOS.get(j).getPayload());
                            elements.get(i).sendKeys(Keys.ENTER);
                            HttpURLConnection c = (HttpURLConnection) new URL(driver.getCurrentUrl()).openConnection();
                            pageSource = getDriver().getPageSource();
                            if (c.getResponseCode() == 500) {
                                setInputValue(payloadDTOS.get(j).getPayload());
                                setResult("서버 응답 거부 : " + c.getResponseCode());
                                payLoadDAO.updatePayLoadCount(payloadDTOS.get(j).getPayload());
                                return true;
                            } else if (checkPageSource(pageSource, KEYWORD[k])) {
                                setInputValue(payloadDTOS.get(j).getPayload());
                                setResult("Os CommandInjection 성공 : " + c.getResponseCode());
                                payLoadDAO.updatePayLoadCount(payloadDTOS.get(j).getPayload());
                                return true;
                            }
                            getDriver().navigate().back();
                            getDriver().navigate().refresh();
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("OS Command Injection Exception");
                e.printStackTrace();
            } finally {
                getDriver().close();
                getDriver().quit();
            }
        }
        setCheckInputTag();
        return false;
    }
}