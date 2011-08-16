<?php
/**
 * This is a simple PHP script that accesses the MongoDB PIX database 
 * and gets the patient information stored there in JSON format. It then 
 * outputs a text file with the "filterlist" i.e. the list of ORBIS patient
 * ids, names, social sec. nbrs, etc. Each row corresponds to a single patient
 * and the fields are separated by the '|' character. 
 *
 * @author Stelios Sfakianakis
 * @version 1.0
 */

ini_set('track_errors', 1);
$a = fopen("http://localhost:28017/pid/patients/", "rb") or
        die ("Failed accessing MongoDB (through REST, have you enabled it??): The error was '$php_errormsg'");

$js = stream_get_contents($a);
fclose($a);
$d = json_decode($js, TRUE);
header('Content-type: text/plain;charset=utf-8');
foreach ($d['rows'] as $r) {
        $orbis_id = '';
        foreach ($r['ids'] as $pid) {
                if ($pid['namespace'] == 'ORBIS') {
                        $orbis_id = $pid['id'];
                        break;
                }
        }
        
        $flds = array($orbis_id, $r['name']['given_name'],  $r['name']['family_name'], $r['date_of_birth'], $r['ssn']);
        echo implode('|', $flds);
        echo "\r\n";
}
?>
