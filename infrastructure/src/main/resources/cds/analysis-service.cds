// AnalysisService — 設計書解析サービス
//
// 入力：GUI が清洗した sheets JSON（multipart 不使用）
// 出力：Job（非同期）/ AnalysisResult（JSON）
//
// すべてのアクションは scope `ifmerge.api` が必要。

namespace handjapan.ifmerge;

service AnalysisService @(path: '/analysis', requires: 'ifmerge.api') {

    action analyze(
        fileName : String,
        sheets   : array of CleanedSheetDto,
        options  : AnalyzeOptions
    ) returns Job;

    function getJob(id : UUID) returns Job;

    function getResult(id : UUID) returns AnalysisResult;
}

type CleanedSheetDto {
    name    : String;
    headers : array of String;
    rows    : array of array of String;
}

type AnalyzeOptions {
    phase1HeadRows : Integer default 30;
    maxChunkRows   : Integer default 100;
}

type Job {
    id          : UUID;
    type        : String enum { ANALYSIS; MERGE };
    status      : String enum { PENDING; RUNNING; SUCCEEDED; FAILED };
    progress    : Integer;
    phase       : String;
    createdAt   : Timestamp;
    startedAt   : Timestamp;
    completedAt : Timestamp;
    error       : ErrorInfo;
}

type ErrorInfo {
    code       : String;
    message    : String;
    occurredAt : Timestamp;
}

type AnalysisResult {
    documentNumber : String;
    ifName         : String;
    dataSheets     : array of DataSheetInfo;
    records        : array of InterfaceRecordDto;
}

type DataSheetInfo {
    sheetName    : String;
    dataStartRow : Integer;
    recordCount  : Integer;
}

type InterfaceRecordDto {
    no              : Integer;
    documentNumber  : String;
    ifName          : String;
    ebsTableName    : String;
    ebsTableId      : String;
    itemId          : String;
    itemName        : String;
    digitCount      : String;
    itemDescription : String;
    dataType        : String;
    digitDecimal    : String;
    devType         : String;
    isKey           : String;
    required        : String;
    remarks         : String;
}
