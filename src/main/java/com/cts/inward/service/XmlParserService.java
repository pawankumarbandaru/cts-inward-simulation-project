package com.cts.inward.service;


import java.io.File;
import java.util.List;

import com.cts.inward.dto.ChequeDTO;

public interface XmlParserService {

    List<ChequeDTO> parseXml(File xmlFile);
}
