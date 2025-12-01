#!/usr/bin/env python3
import urllib.request
import json

try:
    print("Probando endpoint /api/data/streaming...")
    response = urllib.request.urlopen('http://localhost:8080/api/data/streaming', timeout=10)
    content = response.read().decode('utf-8')
    
    if not content:
        print("ERROR: Respuesta vacía")
        exit(1)
    
    print(f"Respuesta recibida ({len(content)} bytes)")
    print("Primeros 300 caracteres:", content[:300])
    
    data = json.loads(content)
    print("\n=== DATOS PARSEADOS ===")
    print("Success:", data.get('success'))
    print("Timestamp:", data.get('timestamp'))
    print("Data count:", len(data.get('data', [])))
    
    if data.get('data'):
        print("\nPrimeros 3 items:")
        for i, item in enumerate(data['data'][:3]):
            print(f"  {i+1}. arcId={item['arcId']}, velocity={item['velocityKmh']:.2f} km/h, lineId={item['lineId']}")
    else:
        print("\nNo hay datos en el array")
        
except Exception as e:
    print("ERROR:", str(e))
    import traceback
    traceback.print_exc()
